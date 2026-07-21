package com.uravugal.matrimony.config;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.uravugal.matrimony.services.UserService;

@Component
public class UserWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private UserService userService;

    // Map of userId -> WebSocketSession for real-time message delivery
    private static final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = getUserIdFromSession(session);
        if (userId != null) {
            userSessions.put(userId, session);
            System.out.println("✅ WebSocket connected for user: " + userId + " (total: " + userSessions.size() + ")");
            userService.updateLastSeen(userId, true);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = getUserIdFromSession(session);
        if (userId != null) {
            userSessions.remove(userId);
            System.out.println("🔴 WebSocket disconnected for user: " + userId + " (total: " + userSessions.size() + ")");
            try {
                userService.updateLastSeen(userId, false);
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
                session.sendMessage(new TextMessage(jsonMessage));
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