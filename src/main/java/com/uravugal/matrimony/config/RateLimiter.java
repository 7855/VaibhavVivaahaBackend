package com.uravugal.matrimony.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimiter {

    // key -> {count, windowStart}
    private final Map<String, long[]> requestCounts = new ConcurrentHashMap<>();

    /**
     * Check if the request is allowed within the rate limit.
     * @param key unique identifier (e.g., IP + endpoint, or mobile number)
     * @param maxRequests max requests allowed in the window
     * @param windowMs time window in milliseconds
     * @return true if allowed, false if rate limited
     */
    public boolean isAllowed(String key, int maxRequests, long windowMs) {
        long now = System.currentTimeMillis();

        requestCounts.compute(key, (k, v) -> {
            if (v == null || (now - v[1]) > windowMs) {
                // New window
                return new long[]{1, now};
            }
            v[0]++;
            return v;
        });

        long[] entry = requestCounts.get(key);
        return entry != null && entry[0] <= maxRequests;
    }
}