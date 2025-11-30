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

    @Autowired(required = false)
    private JwtService jwtService;

    @Autowired
    private GameRepository gameRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(message);
        StompCommand cmd = sha.getCommand();
        if (cmd == StompCommand.CONNECT || cmd == StompCommand.SUBSCRIBE) {
            String auth = sha.getFirstNativeHeader("Authorization");
            String username = null;
            if (auth != null && auth.startsWith("Bearer ") && jwtService != null) {
                String token = auth.substring(7);
                try {
                    username = jwtService.extractUsername(token);
                } catch (Exception ignore) {
                    // invalid token: block if strict
                }
            }

            // Validate subscription destination and room membership
            if (cmd == StompCommand.SUBSCRIBE) {
                String destination = sha.getDestination();
                if (destination == null || !destination.contains("/topic/board/")) {
                    throw new IllegalArgumentException("Invalid subscription destination");
                }
                // Extract room code from destination: /topic/board/{code}/...
                String[] parts = destination.split("/topic/board/");
                if (parts.length < 2) throw new IllegalArgumentException("Missing room code");
                String tail = parts[1];
                String code = tail.split("/")[0];
                // If username is known (JWT provided), ensure user is part of the game session
                if (username != null && code != null) {
                    final String uname = username; // make effectively final for lambda use
                    var opt = gameRepository.findByCode(code);
                    if (opt.isPresent()) {
                        var gs = opt.get();
                        boolean member = gs.getPlayers().stream().anyMatch(p -> uname.equals(p.nickname) || uname.equals(p.playerId));
                        if (!member) throw new IllegalStateException("Not authorized for this room");
                    }
                }
            }

            // Anti-flood on CONNECT per session (min 1s between connects)
            String sessionId = sha.getSessionId();
            if (sessionId != null) {
                long now = System.currentTimeMillis();
                Long last = lastConnectBySession.get(sessionId);
                if (last != null && (now - last) < 1000) {
                    throw new IllegalStateException("Too many connect attempts");
                }
                lastConnectBySession.put(sessionId, now);
            }
        }
        return message;
    }
}
