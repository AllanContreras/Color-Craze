package com.Color_craze.board.controllers;


import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.Color_craze.board.dtos.Requests.PlayerMoveRoomMessage;
import com.Color_craze.board.services.GameService;
import com.Color_craze.board.services.MoveRateLimiter;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class GameSocketController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MoveRateLimiter rateLimiter;

    @MessageMapping("/move")
    public void handlePlayerMove(@Payload PlayerMoveRoomMessage moveMessage) {
        String code = moveMessage.getCode();
        String playerId = moveMessage.getPlayerId();
        if (!rateLimiter.allow(code, playerId)) {
            // Drop excessive messages silently to protect game loop; optionally send 429 via a queue
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
