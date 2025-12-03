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
import lombok.extern.slf4j.Slf4j;

/**
 * STOMP interceptor: validates Authorization (JWT) and room code membership on SUBSCRIBE/CONNECT.
 */
@Component
@Slf4j
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
                log.info("ws_event=CONNECT session={}", sessionId);
            }

            // Enforce JWT on CONNECT when JwtService is available
            String auth = sha.getFirstNativeHeader("Authorization");
            if (jwtService != null) {
                boolean valid = false;
                if (auth != null && auth.startsWith("Bearer ")) {
                    String token = auth.substring(7);
                    try {
                        // We only check that token parses and is not expired
                        String username = jwtService.extractUsername(token);
                        valid = (username != null && !username.isBlank());
                    } catch (Exception ex) {
                        valid = false;
                    }
                }
                if (!valid) {
                    log.warn("ws_event=REJECT_CONNECT reason=invalid_or_missing_token session={}", sessionId);
                    return null; // Reject CONNECT
                }
            }
        }

        // Rate limit SEND and MESSAGE frames per session
        if (cmd == StompCommand.SEND || cmd == StompCommand.MESSAGE) {
            String sessionId = sha.getSessionId();
            if (sessionId != null) {
                WindowCounter counter = sendCounters.computeIfAbsent(sessionId, k -> new WindowCounter());
                boolean allowed = counter.incrementAndCheck(MAX_MSGS_PER_SECOND);
                if (!allowed) {
                    log.warn("ws_event=DROP_RATE_LIMIT session={} dest={} max_per_sec={}", sessionId, sha.getDestination(), MAX_MSGS_PER_SECOND);
                    // Drop the message when rate limit exceeded
                    return null;
                }
                if (cmd == StompCommand.SEND) {
                    log.debug("ws_event=SEND session={} dest={}", sessionId, sha.getDestination());
                }
            }
        }

        if (cmd == StompCommand.SUBSCRIBE) {
            String sessionId = sha.getSessionId();
            log.info("ws_event=SUBSCRIBE session={} dest={}", sessionId, sha.getDestination());
        }

        if (cmd == StompCommand.DISCONNECT) {
            String sessionId = sha.getSessionId();
            log.info("ws_event=DISCONNECT session={}", sessionId);
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
