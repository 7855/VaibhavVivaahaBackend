package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.config.PresenceTracker;
import com.uravugal.matrimony.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.context.event.EventListener;

import java.util.Map;

@Controller
public class PresenceController {

    @Autowired
    private UserService userService;

    @Autowired
    private PresenceTracker presenceTracker;

    @MessageMapping("/user/online")
    public void userOnline(@Payload Map<String, Object> data, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = Long.valueOf(data.get("userId").toString());
        String sessionId = headerAccessor.getSessionId();
        presenceTracker.registerSession(sessionId, userId);
        userService.updateLastSeen(userId, true);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Long userId = presenceTracker.getUserId(sessionId);
        if (userId != null) {
            userService.updateLastSeen(userId, false);
            presenceTracker.removeSession(sessionId);
        }
    }
}
