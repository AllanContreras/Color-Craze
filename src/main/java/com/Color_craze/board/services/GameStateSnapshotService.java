package com.Color_craze.board.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;

/**
 * Snapshot persistence for GameSession state.
 * Uses Redis if configured; otherwise falls back to in-memory map.
 */
@Component
public class GameStateSnapshotService {
    @Autowired(required = false)
    private StringRedisTemplate redis;

    private final Map<String, String> fallback = new ConcurrentHashMap<>();
    @Value("${redis.ttl.seconds:86400}")
    private long ttlSeconds;

    public void save(String code, String json) {
        String key = key(code);
        if (redis != null) {
            redis.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
        } else {
            fallback.put(key, json);
        }
    }

    public String load(String code) {
        String key = key(code);
        if (redis != null) {
            return redis.opsForValue().get(key);
        } else {
            return fallback.get(key);
        }
    }

    public void delete(String code) {
        String key = key(code);
        if (redis != null) {
            redis.delete(key);
        } else {
            fallback.remove(key);
        }
    }

    private String key(String code) { return "game:" + code; }

    // Board snapshots (positions + platforms)
    public void saveBoard(String code, String json) {
        String key = boardKey(code);
        if (redis != null) {
            redis.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
        } else {
            fallback.put(key, json);
        }
    }
    public String loadBoard(String code) {
        String key = boardKey(code);
        if (redis != null) {
            return redis.opsForValue().get(key);
        } else {
            return fallback.get(key);
        }
    }
    public void deleteBoard(String code) {
        String key = boardKey(code);
        if (redis != null) {
            redis.delete(key);
        } else {
            fallback.remove(key);
        }
    }
    private String boardKey(String code) { return "board:" + code; }
}
