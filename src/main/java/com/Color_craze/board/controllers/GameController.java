package com.Color_craze.board.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Color_craze.board.dtos.CreateGameResponse;
import com.Color_craze.board.dtos.GameInfoResponse;
import com.Color_craze.board.dtos.JoinGameRequest;
import com.Color_craze.board.dtos.UpdatePlayerRequest;
import com.Color_craze.board.dtos.UpdateThemeRequest;
import com.Color_craze.board.services.GameService;
import com.Color_craze.board.dtos.GameInfoLiteResponse;

import lombok.RequiredArgsConstructor;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @PostMapping
    public ResponseEntity<CreateGameResponse> createGame() {
        return ResponseEntity.ok(gameService.createGame());
    }

    @GetMapping("/{code}")
    public ResponseEntity<GameInfoResponse> getGame(@PathVariable String code) {
        return gameService.getGame(code)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<Void> join(@PathVariable String code, @RequestBody JoinGameRequest req) {
        gameService.joinGame(code, req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{code}/restart")
    public ResponseEntity<Void> restart(@PathVariable String code) {
        gameService.restartGame(code);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{code}/player")
    public ResponseEntity<Void> updatePlayer(@PathVariable String code, @RequestBody UpdatePlayerRequest req) {
        gameService.updatePlayer(code, req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{code}/theme")
    public ResponseEntity<Void> updateTheme(@PathVariable String code, @RequestBody UpdateThemeRequest req) {
        gameService.updateTheme(code, req);
        return ResponseEntity.ok().build();
    }
    
    // Endpoint para versión ligera
    @GetMapping("/{code}/lite")
    public ResponseEntity<GameInfoLiteResponse> getGameLite(@PathVariable String code) {
        return gameService.getGameLite(code)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
