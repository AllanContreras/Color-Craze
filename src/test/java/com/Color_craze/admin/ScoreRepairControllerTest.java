package com.Color_craze.admin;

import com.Color_craze.board.models.GameSession;
import com.Color_craze.board.repositories.GameRepository;
import com.Color_craze.utils.enums.ColorStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ScoreRepairControllerTest {

    @Test
    void repairScores_recalculates_from_platformStates() {
        // Arrange
        GameRepository repo = Mockito.mock(GameRepository.class);
        var gs = new GameSession();
        gs.setCode("ABC123");
        // Players YELLOW and PINK
        var p1 = new GameSession.PlayerEntry("p1", "Alice", ColorStatus.YELLOW, "ROBOT");
        var p2 = new GameSession.PlayerEntry("p2", "Bob", ColorStatus.PINK, "ALIEN");
        gs.getPlayers().add(p1);
        gs.getPlayers().add(p2);
        // Platform states: 3 yellow, 1 pink
        gs.setPlatforms(List.of(
            new GameSession.PlatformState(0,0, ColorStatus.YELLOW),
            new GameSession.PlatformState(0,1, ColorStatus.YELLOW),
            new GameSession.PlatformState(1,0, ColorStatus.YELLOW),
            new GameSession.PlatformState(2,2, ColorStatus.PINK)
        ));
        Mockito.when(repo.findByCode("ABC123")).thenReturn(Optional.of(gs));
        Mockito.when(repo.save(Mockito.any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));

        var controller = new SnapshotAdminController(null, repo);

        // Act
        ResponseEntity<Map<String,Object>> resp = controller.repairScores("ABC123");

        // Assert
        assertEquals(200, resp.getStatusCode().value());
        var body = resp.getBody();
        assertNotNull(body);
        assertEquals("ABC123", body.get("code"));
        var scoreboard = (List<Map<String,Object>>) body.get("scoreboard");
        assertNotNull(scoreboard);
        // First entry should be YELLOW with 3 points
        Map<String,Object> first = scoreboard.get(0);
        assertEquals("Alice", first.get("nickname"));
        assertEquals(3, first.get("score"));
        // Second entry PINK with 1 point
        Map<String,Object> second = scoreboard.get(1);
        assertEquals("Bob", second.get("nickname"));
        assertEquals(1, second.get("score"));
    }

    @Test
    void repairScores_conflict_if_no_platforms() {
        GameRepository repo = Mockito.mock(GameRepository.class);
        var gs = new GameSession();
        gs.setCode("XYZ999");
        Mockito.when(repo.findByCode("XYZ999")).thenReturn(Optional.of(gs));
        var controller = new SnapshotAdminController(null, repo);
        var resp = controller.repairScores("XYZ999");
        assertEquals(409, resp.getStatusCode().value());
        assertTrue(resp.getBody().get("message").toString().contains("No hay plataformas"));
    }
}
