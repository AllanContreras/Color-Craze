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
            if (jwtService == null) {
                throw new IllegalStateException("Authorization service unavailable");
            }
            if (auth == null || !auth.startsWith("Bearer ")) {
                throw new IllegalStateException("Unauthorized: missing Bearer token");
            }
            String token = auth.substring(7);
            try {
                username = jwtService.extractUsername(token);
                if (!jwtService.isTokenValid(token, null)) { // validation against user details is handled elsewhere
                    throw new IllegalStateException("Unauthorized: invalid token");
                }
            } catch (Exception ex) {
                throw new IllegalStateException("Unauthorized: token error");
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
                // Ensure user is part of the game session
                if (code != null) {
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
