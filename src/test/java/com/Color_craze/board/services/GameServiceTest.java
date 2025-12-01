package com.Color_craze.board.services;

import com.Color_craze.board.dtos.JoinGameRequest;
import com.Color_craze.board.models.GameSession;
import com.Color_craze.board.repositories.GameRepository;
import com.Color_craze.board.arena.services.ArenaService;
import com.Color_craze.utils.enums.ColorStatus;
import com.Color_craze.utils.enums.PlayerMove;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Focused unit tests for {@link GameService} targeting color assignment, avatar sanitization
 * and per-player move rate limiting logic.
 */
class GameServiceTest {

    private GameRepository gameRepository;
    private BoardService boardService; // use real implementation for move mechanics
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
        meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        gameService = new GameService(gameRepository, boardService, messagingTemplate, moveRateLimiter, arenaService, meterRegistry);
    }

    @Test
    void joinGameAssignsUniqueColorsAndSanitizesAvatar() {
        GameSession gs = new GameSession();
        gs.setCode("ABC123");
        gs.setStatus("WAITING");
        gs.setTheme("metal");
        gs.setJoinDeadline(Instant.now().plusSeconds(30));
        when(gameRepository.findByCode("ABC123")).thenReturn(Optional.of(gs));
        when(gameRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // First player joins with valid avatar
        gameService.joinGame("ABC123", new JoinGameRequest("p1", "Nick1", null, "ALIEN"));
        // Second player joins with legacy avatar value that should map to WITCH
        gameService.joinGame("ABC123", new JoinGameRequest("p2", "Nick2", null, "princess"));
        // Third player joins with invalid avatar which should fallback to ROBOT
        gameService.joinGame("ABC123", new JoinGameRequest("p3", "Nick3", null, "invalidAvatar"));

        assertEquals(3, gs.getPlayers().size(), "Debe haber 3 jugadores en la sesión");
        java.util.Set<ColorStatus> colors = new java.util.HashSet<>();
        for (var p : gs.getPlayers()) {
            assertTrue(p.color == ColorStatus.YELLOW || p.color == ColorStatus.PINK || p.color == ColorStatus.PURPLE || p.color == ColorStatus.GREEN,
                "Color asignado debe estar dentro del conjunto permitido");
            assertTrue(colors.add(p.color), "Color duplicado detectado: " + p.color);
        }
        var p2 = gs.getPlayers().stream().filter(p -> p.playerId.equals("p2")).findFirst().orElseThrow();
        assertEquals("WITCH", p2.avatar, "Avatar 'princess' debe migrarse a 'WITCH'");
        var p3 = gs.getPlayers().stream().filter(p -> p.playerId.equals("p3")).findFirst().orElseThrow();
        assertEquals("ROBOT", p3.avatar, "Avatar inválido debe normalizarse a 'ROBOT'");
    }

    @Test
    void joinGameRoomFullThrows() {
        GameSession gs = new GameSession();
        gs.setCode("ROOMX");
        gs.setStatus("WAITING");
        gs.setTheme("cyber");
        gs.setJoinDeadline(Instant.now().plusSeconds(30));
        when(gameRepository.findByCode("ROOMX")).thenReturn(Optional.of(gs));
        when(gameRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Llenar la sala con 4 jugadores
        for (int i = 1; i <= 4; i++) {
            gameService.joinGame("ROOMX", new JoinGameRequest("p" + i, "N" + i, null, "ROBOT"));
        }
        assertEquals(4, gs.getPlayers().size());
        // Intentar un quinto
        var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
            gameService.joinGame("ROOMX", new JoinGameRequest("p5", "N5", null, "ROBOT"))
        );
        assertTrue(ex.getReason().contains("Room full"));
    }

    @Test
    void handlePlayerMoveRateLimitingWorks() {
        GameSession gs = new GameSession();
        gs.setCode("PLAY1");
        gs.setStatus("PLAYING");
        // Player entry with fixed UUID to keep board mapping stable
        String playerId = UUID.randomUUID().toString();
        gs.getPlayers().add(new GameSession.PlayerEntry(playerId, "Runner", ColorStatus.YELLOW, "ROBOT"));
        when(gameRepository.findByCode("PLAY1")).thenReturn(Optional.of(gs));
        when(gameRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // First 20 moves should succeed (may paint cells)
        int success = 0;
        for (int i = 0; i < 20; i++) {
            var payload = gameService.handlePlayerMove("PLAY1", playerId, PlayerMove.RIGHT);
            if (payload != null) {
                assertFalse(payload.containsKey("rateLimited"), "No debería marcarse rateLimited aún");
                success++;
            }
        }
        assertEquals(20, success, "Se esperaban 20 movimientos aceptados antes del límite");
        // 21st move should be rate limited
        var limited = gameService.handlePlayerMove("PLAY1", playerId, PlayerMove.RIGHT);
        assertNotNull(limited, "Payload no debe ser null para movimiento rate-limited");
        assertTrue(Boolean.TRUE.equals(limited.get("rateLimited")), "Debe indicar rateLimited=true en el movimiento excedente");
        assertEquals(playerId, limited.get("playerId"));
    }
}
