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
import io.micrometer.core.instrument.MeterRegistry;

@Controller
@RequiredArgsConstructor
public class GameSocketController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MoveRateLimiter rateLimiter;
    private static final Logger log = LoggerFactory.getLogger(GameSocketController.class);
    private final MeterRegistry meterRegistry;

    @MessageMapping("/move")
    public void handlePlayerMove(@Payload PlayerMoveRoomMessage moveMessage) {
        String code = moveMessage.getCode();
        String playerId = moveMessage.getPlayerId();
        boolean allowed = rateLimiter.allow(code, playerId);
        if (!allowed) {
            log.warn("[ALERTA] Rate limit EXCEDIDO: game={}, playerId={}, timestamp={}. Mensaje DESCARTADO.", code, playerId, System.currentTimeMillis());
            try { meterRegistry.counter("ws.move.rate_limited").increment(); } catch (Exception ignored) {}
            return; // Mensaje descartado
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
