package com.Color_craze.board.controllers;


import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.Color_craze.board.dtos.Requests.PlayerMoveRoomMessage;
import com.Color_craze.board.services.GameService;
import com.Color_craze.board.services.MoveRateLimiter;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequiredArgsConstructor
public class GameSocketController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MoveRateLimiter rateLimiter;
    private static final Logger log = LoggerFactory.getLogger(GameSocketController.class);

    @MessageMapping("/move")
    public void handlePlayerMove(@Payload PlayerMoveRoomMessage moveMessage) {
        String code = moveMessage.getCode();
        String playerId = moveMessage.getPlayerId();
        if (!rateLimiter.allow(code, playerId)) {
            // Drop excessive messages to protect game loop; log WARN for audit/monitoring
            try { log.warn("Rate limit exceeded: game={}, playerId={}", code, playerId); } catch (Exception ignored) {}
            return;
        }
        Object payload = gameService.handlePlayerMove(code, playerId, moveMessage.getDirection());
        if (payload instanceof java.util.Map) {
            Object success = ((java.util.Map<?,?>)payload).get("success");
            if (Boolean.TRUE.equals(success)) {
                messagingTemplate.convertAndSend(String.format("/topic/board/%s", code), payload);
            }
        }
    }
}
