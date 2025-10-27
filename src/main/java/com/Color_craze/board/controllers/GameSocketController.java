package com.Color_craze.board.controllers;


import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.Color_craze.board.dtos.Requests.PlayerMoveRoomMessage;
import com.Color_craze.board.services.GameService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class GameSocketController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/move")
    public void handlePlayerMove(@Payload PlayerMoveRoomMessage moveMessage) {
        String code = moveMessage.getCode();
        Object payload = gameService.handlePlayerMove(code, moveMessage.getPlayerId(), moveMessage.getDirection());
        if (payload instanceof java.util.Map) {
            Object success = ((java.util.Map<?,?>)payload).get("success");
            if (Boolean.TRUE.equals(success)) {
                messagingTemplate.convertAndSend(String.format("/topic/board/%s", code), payload);
            }
        }
    }
}
