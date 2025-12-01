package com.Color_craze.board.services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.Color_craze.board.models.GameSession;
import com.Color_craze.board.repositories.GameRepository;
import com.Color_craze.utils.enums.ColorStatus;
import com.Color_craze.board.arena.services.ArenaService;

import static org.mockito.Mockito.*;

/**
 * Verifies session duration metric is recorded on endGame.
 */
public class GameServiceDurationMetricsTest {
    @Test
    void recordsSessionDurationMetric() {
        GameRepository repo = mock(GameRepository.class);
        BoardService board = new BoardService();
        SimpMessagingTemplate tmpl = mock(SimpMessagingTemplate.class);
        ArenaService arena = mock(ArenaService.class);
        MoveRateLimiter limiter = new MoveRateLimiter();
        io.micrometer.core.instrument.simple.SimpleMeterRegistry meter = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        GameService service = new GameService(repo, board, tmpl, limiter, arena, meter);

        GameSession gs = new GameSession();
        gs.setCode("DUR1");
        gs.setStatus("PLAYING");
        gs.setStartedAt(Instant.now().minusSeconds(5));
        gs.getPlayers().add(new GameSession.PlayerEntry("p1", "Player", ColorStatus.YELLOW, "ROBOT"));
        when(repo.findByCode("DUR1")).thenReturn(Optional.of(gs));
        when(repo.save(any(GameSession.class))).thenAnswer(i -> i.getArgument(0));

        // invoke endGame
        service.restartGame("DUR1"); // restart sets WAITING; simulate a short play by calling start then end
        // start
        gs.setStatus("PLAYING");
        gs.setStartedAt(Instant.now().minusSeconds(2));
        service.handlePlayerMove("DUR1", "p1", com.Color_craze.utils.enums.PlayerMove.RIGHT); // ensure board init
        // end
        when(repo.findByCode("DUR1")).thenReturn(Optional.of(gs));
        try {
            var endMethod = GameService.class.getDeclaredMethod("endGame", String.class);
            endMethod.setAccessible(true);
            endMethod.invoke(service, "DUR1");
        } catch (Exception e) {
            fail("Reflection endGame invocation failed: " + e.getMessage());
        }
        var summary = meter.find("game.session.duration.ms").summary();
        assertNotNull(summary, "Duration summary debe existir");
        assertEquals(1, summary.count(), "Debe haberse registrado 1 duración");
        assertTrue(summary.totalAmount() > 0.0, "La duración registrada debe ser > 0");
    }
}
