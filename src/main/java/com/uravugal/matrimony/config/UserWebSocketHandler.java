package com.uravugal.matrimony.config;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.uravugal.matrimony.services.ChatService;
import com.uravugal.matrimony.services.UserService;

@Component
public class UserWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private UserService userService;

    // @Lazy: ChatService calls this class's STATIC methods only (never injects it), so there is
    // no cycle today — lazy keeps it that way if that ever changes.
    @Autowired
    @org.springframework.context.annotation.Lazy
    private ChatService chatService;

    // Map of userId -> WebSocketSession for real-time message delivery
    private static final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    /** Session attribute: when we last heard ANYTHING from this client (ms since epoch). */
    private static final String LAST_SEEN_ATTR = "lastHeardFromMs";

    // The app pings every 25s (webSocketService.PING_EVERY_MS). Three missed pings means the
    // client is gone; anything shorter would flap a member offline on a brief tunnel or a
    // network switch, which the reconnect logic handles on its own.
    private static final long STALE_AFTER_MS = 75_000L;

    private static void touch(WebSocketSession session) {
        try { session.getAttributes().put(LAST_SEEN_ATTR, System.currentTimeMillis()); }
        catch (Exception ignored) { }
    }

    /**
     * 🔴 Why this exists: a force-killed app never closes its socket cleanly. No FIN reaches the
     * server, so afterConnectionClosed does NOT fire, the session stays in userSessions and
     * isUserOnline() keeps answering true — the partner sitting in that chat goes on seeing
     * "Online" until the OS TCP timeout, which can be hours. The client's own heartbeat cannot
     * fix this: it only lets the CLIENT notice a dead SERVER, never the reverse.
     *
     * So the server times clients out itself. Closing the session is all this does — the close
     * fires afterConnectionClosed, which already removes the entry, writes isOnline=false and
     * broadcasts presence. One cleanup path, not two that can drift apart.
     */
    @Scheduled(fixedDelay = 30_000L)
    public void reapStaleSessions() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Long, WebSocketSession>> it = userSessions.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Long, WebSocketSession> entry = it.next();
            Long userId = entry.getKey();
            WebSocketSession session = entry.getValue();
            try {
                Object last = session.getAttributes().get(LAST_SEEN_ATTR);
                long lastMs = (last instanceof Number) ? ((Number) last).longValue() : 0L;
                boolean stale = lastMs > 0 && (now - lastMs) > STALE_AFTER_MS;

                if (!session.isOpen()) {
                    // Already dead but still mapped: afterConnectionClosed will not fire again,
                    // so finish the cleanup here rather than leave them showing as online.
                    if (userSessions.remove(userId, session)) {
                        System.out.println("🧹 Reaped closed session for user " + userId);
                        markOffline(userId);
                    }
                    continue;
                }
                if (stale) {
                    System.out.println("🧹 Reaping stale session for user " + userId
                            + " (silent " + ((now - lastMs) / 1000) + "s)");
                    // Triggers afterConnectionClosed, which does the real cleanup.
                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
                }
            } catch (Exception e) {
                // Closing a half-dead socket can throw — make sure presence still clears.
                if (userSessions.remove(userId, session)) markOffline(userId);
            }
        }
    }

    private void markOffline(Long userId) {
        try {
            userService.updateLastSeen(userId, false);
            chatService.broadcastPresence(userId, false);
        } catch (Exception e) {
            System.out.println("⚠️ Reaper could not mark user " + userId + " offline: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = getUserIdFromSession(session);
        if (userId != null) {
            // A phone that reconnects (app resumed, network switch) opens its NEW socket before
            // the old one has finished closing. Retire the old session explicitly so its late
            // close event cannot be mistaken for the member going offline (see below).
            WebSocketSession previous = userSessions.put(userId, session);
            if (previous != null && previous != session && previous.isOpen()) {
                try { previous.close(CloseStatus.NORMAL); } catch (Exception ignored) { }
            }
            touch(session);
            System.out.println("✅ WebSocket connected for user: " + userId + " (total: " + userSessions.size() + ")");
            userService.updateLastSeen(userId, true);
            // Everything queued for them is now delivered (single → double grey tick), and the
            // people they chat with see "Online" straight away.
            chatService.onUserConnected(userId);
            chatService.broadcastPresence(userId, true);
        }
    }

    /** Heartbeat: the app pings every ~25s; a missing pong tells it the socket is dead. */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Any inbound frame proves the client is alive, so refresh its timestamp before
        // anything else — the 25s ping is what normally keeps a session out of the reaper.
        touch(session);
        try {
            if (message.getPayload() != null && message.getPayload().contains("\"ping\"")) {
                synchronized (session) {
                    if (session.isOpen()) session.sendMessage(new TextMessage("{\"type\":\"pong\",\"data\":{}}"));
                }
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = getUserIdFromSession(session);
        if (userId != null) {
            // 🔴 remove(key, value), NOT remove(key). The old one-argument form deleted whatever
            // session was mapped for this user — so when a reconnecting phone's OLD socket closed
            // a moment after its NEW one had registered, it wiped the new entry and flagged the
            // member offline while they sat in an open chat. Live messages then stopped arriving
            // (push notifications still did, which is exactly what the client reported).
            boolean wasCurrent = userSessions.remove(userId, session);
            System.out.println("🔴 WebSocket disconnected for user: " + userId + " (current=" + wasCurrent + ", total: " + userSessions.size() + ")");
            if (!wasCurrent) return; // a newer session is live — the member is still online
            try {
                userService.updateLastSeen(userId, false);
                chatService.broadcastPresence(userId, false);
            } catch (Exception e) {
                // Tomcat force-closes every open WebSocket session as part of app shutdown
                // (server stop/restart), which fires this handler AFTER the Spring context has
                // already started closing. Any bean/JPA lookup at that point fails — surfaces as
                // ConfigurationPropertiesBindException wrapping an IllegalStateException
                // ("...has been closed already"), not the IllegalStateException directly, so this
                // must catch broadly. Harmless (the app is going down anyway); just don't let it
                // spam the shutdown log with a huge stack trace.
                System.out.println("⚠️ Skipped updateLastSeen for user " + userId + " during shutdown: " + e.getMessage());
            }
        }
    }

    /**
     * Send a message to a specific user via their WebSocket session.
     * Called by ChatService after saving a message to DB.
     */
    public static void sendMessageToUser(Long userId, String jsonMessage) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                // WebSocketSession is not safe for concurrent sends; chat, read-receipt,
                // delivery and presence pushes can now all target the same member at once.
                synchronized (session) {
                    session.sendMessage(new TextMessage(jsonMessage));
                }
                System.out.println("📨 WebSocket message sent to user: " + userId);
            } catch (IOException e) {
                System.out.println("⚠️ Failed to send WebSocket message to user " + userId + ": " + e.getMessage());
            }
        }
    }

    /**
     * Check if a user is currently connected via WebSocket.
     */
    public static boolean isUserOnline(Long userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }

    private Long getUserIdFromSession(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) return null;
            String query = uri.getQuery(); // userId=34
            return Long.parseLong(query.split("=")[1]);
        } catch (Exception e) {
            System.out.println("⚠️ Failed to parse userId from WebSocket session: " + e.getMessage());
            return null;
        }
    }
}