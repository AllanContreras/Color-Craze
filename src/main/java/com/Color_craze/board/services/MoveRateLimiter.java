package com.Color_craze.board.services;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/**
 * Simple token bucket style limiter (in-memory) for movement messages.
 * Policy: max 20 moves per rolling 1s window per (gameCode, playerId).
 */
@Component
public class MoveRateLimiter {

    private static class Counter { long windowStartMs; int used; }

    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    private static final int LIMIT = 20;
    private static final long WINDOW_MS = 1000L;

    public boolean allow(String gameCode, String playerId) {
        String key = gameCode + "|" + playerId;
        long now = System.currentTimeMillis();
        Counter c = counters.computeIfAbsent(key, k -> new Counter());
        if (now - c.windowStartMs >= WINDOW_MS) {
            c.windowStartMs = now;
            c.used = 0;
        }
        if (c.used >= LIMIT) {
            System.out.println("[AUDIT] MoveRateLimiter BLOCKED: game=" + gameCode + ", playerId=" + playerId + ", timestamp=" + now);
            return false;
        }
        c.used++;
        return true;
    }
}
