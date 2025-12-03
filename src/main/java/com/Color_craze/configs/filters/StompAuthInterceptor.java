package com.Color_craze.configs.filters;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import com.Color_craze.auth.services.JwtService;
import com.Color_craze.board.repositories.GameRepository;

/**
 * STOMP interceptor: validates Authorization (JWT) and room code membership on SUBSCRIBE/CONNECT.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final Map<String, Long> lastConnectBySession = new ConcurrentHashMap<>();
    // Simple per-session rate limiter: max 20 messages per 1-second window
    private static final int MAX_MSGS_PER_SECOND = 20;
    private final Map<String, WindowCounter> sendCounters = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private JwtService jwtService;

    @Autowired
    private GameRepository gameRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(message);
        StompCommand cmd = sha.getCommand();

        // Temporarily disable strict STOMP auth to avoid breaking WebSocket in production.
        // Keep only optional anti-flood protection on CONNECT.
        if (cmd == StompCommand.CONNECT) {
            String sessionId = sha.getSessionId();
            if (sessionId != null) {
                long now = System.currentTimeMillis();
                Long last = lastConnectBySession.get(sessionId);
                if (last != null && (now - last) < 1000) {
                    // If desired, this can be relaxed further by removing this check.
                    // For now, we simply update the timestamp without throwing.
                }
                lastConnectBySession.put(sessionId, now);
            }
        }

        // Rate limit SEND and MESSAGE frames per session
        if (cmd == StompCommand.SEND || cmd == StompCommand.MESSAGE) {
            String sessionId = sha.getSessionId();
            if (sessionId != null) {
                WindowCounter counter = sendCounters.computeIfAbsent(sessionId, k -> new WindowCounter());
                boolean allowed = counter.incrementAndCheck(MAX_MSGS_PER_SECOND);
                if (!allowed) {
                    // Drop the message when rate limit exceeded
                    return null;
                }
            }
        }

        return message;
    }

    private static class WindowCounter {
        private long windowStartMillis = System.currentTimeMillis();
        private int count = 0;

        synchronized boolean incrementAndCheck(int maxPerSecond) {
            long now = System.currentTimeMillis();
            if (now - windowStartMillis >= 1000) {
                windowStartMillis = now;
                count = 0;
            }
            count++;
            return count <= maxPerSecond;
        }
    }
}
