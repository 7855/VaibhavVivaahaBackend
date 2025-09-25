package com.uravugal.matrimony.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.uravugal.matrimony.services.UserService;

@Component
public class UserWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private UserService userService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = getUserIdFromSession(session);
        System.out.println("✅ WebSocket connected for user: " + userId);
        userService.updateLastSeen(userId, true);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = getUserIdFromSession(session);
        System.out.println("🔴 WebSocket disconnected for user: " + userId);
        userService.updateLastSeen(userId, false);
    }

    private Long getUserIdFromSession(WebSocketSession session) {
        // You must correctly parse this
        URI uri = session.getUri(); // Example: ws://localhost:9100/ws/user-status?userId=34
        String query = uri.getQuery(); // userId=34
        return Long.parseLong(query.split("=")[1]);
    }
}

