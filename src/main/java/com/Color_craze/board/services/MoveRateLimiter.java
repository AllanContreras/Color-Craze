package com.Color_craze.board.services;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

/**
 * Centralized rate limiter for player movement messages using Bucket4j.
 * Policy: max 20 moves per second per (gameCode, playerId).
 */
@Component
public class MoveRateLimiter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(20, Refill.greedy(20, Duration.ofSeconds(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket getBucket(String key) {
        return buckets.computeIfAbsent(key, k -> createBucket());
    }

    /**
     * Returns true if the move is allowed under the current rate limit.
     */
    public boolean allow(String gameCode, String playerId) {
        String key = gameCode + "|" + playerId;
        Bucket b = getBucket(key);
        return b.tryConsume(1);
    }
}
