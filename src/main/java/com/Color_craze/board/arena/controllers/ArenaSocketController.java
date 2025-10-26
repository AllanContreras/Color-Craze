package com.Color_craze.board.arena.controllers;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import com.Color_craze.board.arena.dtos.ArenaInput;
import com.Color_craze.board.arena.services.ArenaService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ArenaSocketController {
    private final ArenaService arenaService;

    @MessageMapping("/arena/input")
    public void input(@Payload ArenaInput input){
        arenaService.updateInput(input);
    }
}
