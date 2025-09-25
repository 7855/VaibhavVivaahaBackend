package com.uravugal.matrimony.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PresenceTracker {
    private final Map<String, Long> sessionUserMap = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, Long userId) {
        sessionUserMap.put(sessionId, userId);
    }
    
    public void removeSession(String sessionId) {
        sessionUserMap.remove(sessionId);
    }

    public Long getUserId(String sessionId) {
        return sessionUserMap.get(sessionId);
    }
}

