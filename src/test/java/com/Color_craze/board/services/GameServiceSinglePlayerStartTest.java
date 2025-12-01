package com.Color_craze.board.services;

import com.Color_craze.board.dtos.JoinGameRequest;
import com.Color_craze.board.models.GameSession;
import com.Color_craze.board.repositories.GameRepository;
import com.Color_craze.board.arena.services.ArenaService;
import com.Color_craze.utils.enums.ColorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Additional coverage for GameService focusing on:
 * - Auto-start with single player triggers CPU bot addition.
 * - restartGame resets scores, platforms snapshot and sets new join window & theme.
 * - updateTheme path rejects manual changes.
 */
class GameServiceSinglePlayerStartTest {
    private GameRepository gameRepository;
    private BoardService boardService;
    private SimpMessagingTemplate messagingTemplate;
    private ArenaService arenaService;
    private MoveRateLimiter moveRateLimiter;
    private GameService gameService;
    private io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setup() {
        gameRepository = mock(GameRepository.class);
        boardService = new BoardService();
        messagingTemplate = mock(SimpMessagingTemplate.class);
        arenaService = mock(ArenaService.class);
        moveRateLimiter = new MoveRateLimiter();
        gameService = new GameService(gameRepository, boardService, messagingTemplate, moveRateLimiter, arenaService, meterRegistry);
    }

    @Test
    void singlePlayerAutoStartAddsCpuBot() {
        String code = "AUTO1";
        GameSession gs = new GameSession();
        gs.setCode(code);
        gs.setStatus("WAITING");
        gs.setTheme("metal");
        gs.setJoinDeadline(Instant.now().minusSeconds(1)); // expired to trigger start
        gs.getPlayers().add(new GameSession.PlayerEntry("p1", "Solo", ColorStatus.YELLOW, "ROBOT"));
        when(gameRepository.findByCode(code)).thenReturn(Optional.of(gs));
        when(gameRepository.save(any(GameSession.class))).thenAnswer(i -> i.getArgument(0));

        // getGame triggers startGame if join window expired
        var infoOpt = gameService.getGame(code);
        assertTrue(infoOpt.isPresent());
        assertEquals("PLAYING", gs.getStatus());
        // Bot should be added making 2 players total
        assertEquals(2, gs.getPlayers().size(), "Debe agregarse un bot CPU cuando hay solo un jugador humano");
        assertTrue(gs.getPlayers().stream().anyMatch(p -> p.nickname.equals("CPU")), "Debe existir jugador CPU");
    }

    @Test
    void restartGameResetsScoresPlatformsAndJoinWindow() {
        String code = "RST1";
        GameSession gs = new GameSession();
        gs.setCode(code);
        gs.setStatus("PLAYING");
        gs.setStartedAt(Instant.now());
        gs.setTheme("cyber");
        gs.getPlayers().add(new GameSession.PlayerEntry("p1", "Player", ColorStatus.PINK, "ALIEN"));
        gs.getPlayers().get(0).score = 42; // non-zero score
        // simulate painted platforms snapshot
        gs.getPlatforms().add(new GameSession.PlatformState(0,0, ColorStatus.PINK));
        when(gameRepository.findByCode(code)).thenReturn(Optional.of(gs));
        when(gameRepository.save(any(GameSession.class))).thenAnswer(i -> i.getArgument(0));

        gameService.restartGame(code);
        assertEquals("WAITING", gs.getStatus());
        assertNull(gs.getStartedAt());
        assertNull(gs.getFinishedAt());
        assertNotNull(gs.getJoinDeadline(), "Debe establecer nueva ventana de unión");
        assertTrue(gs.getPlayers().stream().allMatch(p -> p.score == 0), "Scores deben reiniciarse a 0");
        assertEquals(0, gs.getPlatforms().size(), "Snapshot de plataformas debe limpiarse");
        assertNotNull(gs.getTheme(), "Tema aleatorio debe estar asignado");
    }

    @Test
    void updateThemeAlwaysConflicts() {
        String code = "THEME1";
        GameSession gs = new GameSession();
        gs.setCode(code);
        gs.setStatus("WAITING");
        gs.setTheme("moon");
        when(gameRepository.findByCode(code)).thenReturn(Optional.of(gs));
        // updateTheme always throws conflict
        var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
            gameService.updateTheme(code, new com.Color_craze.board.dtos.UpdateThemeRequest("p1", "metal"))
        );
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatusCode());
    }
}
