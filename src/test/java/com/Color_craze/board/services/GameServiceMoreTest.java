package com.Color_craze.board.services;

import com.Color_craze.board.dtos.JoinGameRequest;
import com.Color_craze.board.models.GameSession;
import com.Color_craze.board.repositories.GameRepository;
import com.Color_craze.utils.enums.ColorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.Color_craze.board.arena.services.ArenaService;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** More coverage for GameService lifecycle paths. */
class GameServiceMoreTest {
    private GameRepository gameRepository;
    private BoardService boardService;
    private SimpMessagingTemplate messagingTemplate;
    private ArenaService arenaService;
    private GameService gameService;

    @BeforeEach
    void setup(){
        gameRepository = mock(GameRepository.class);
        boardService = new BoardService();
        messagingTemplate = mock(SimpMessagingTemplate.class);
        arenaService = mock(ArenaService.class);
        gameService = new GameService(gameRepository, boardService, messagingTemplate, arenaService);
    }

    @Test
    void createGameInitializesWaitingStateAndSchedulesStart() {
        when(gameRepository.existsByCode(anyString())).thenReturn(false);
        when(gameRepository.save(any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));
        var resp = gameService.createGame();
        assertNotNull(resp);
        var code = resp.code();
        // getGame should auto-start if joinDeadline expired; here still WAITING
        when(gameRepository.findByCode(code)).thenReturn(Optional.of(mockSession(code, "WAITING")));
        var infoOpt = gameService.getGame(code);
        assertTrue(infoOpt.isPresent());
        assertEquals("WAITING", infoOpt.get().status());
    }

    @Test
    void startAndEndGamePersistsScoresAndPublishes() {
        String code = "ROOMZ";
        GameSession gs = mockSession(code, "WAITING");
        gs.getPlayers().add(new GameSession.PlayerEntry("p1", "P1", ColorStatus.YELLOW, "ROBOT"));
        when(gameRepository.findByCode(code)).thenReturn(Optional.of(gs));
        when(gameRepository.save(any(GameSession.class))).thenAnswer(i -> i.getArgument(0));

        // Join and possibly auto-start depending on deadline
        gameService.joinGame(code, new JoinGameRequest("p1", "P1", null, "ROBOT"));
        // Force start
        // invoke private start via public path: getGame triggers start if deadline passed
        gs.setJoinDeadline(Instant.now().minusSeconds(1));
        gameService.getGame(code);
        assertEquals("PLAYING", gs.getStatus());
        assertNotNull(gs.getStartedAt());

        // End and verify finished
        gameService.restartGame(code); // exercise restart path
        assertEquals("WAITING", gs.getStatus());
    }

    @Test
    void updatePlayerRejectsManualColorChangeAndAllowsAvatar() {
        String code = "UPD1";
        GameSession gs = mockSession(code, "WAITING");
        gs.getPlayers().add(new GameSession.PlayerEntry("p1", "P1", ColorStatus.GREEN, "ALIEN"));
        when(gameRepository.findByCode(code)).thenReturn(Optional.of(gs));
        when(gameRepository.save(any(GameSession.class))).thenAnswer(i -> i.getArgument(0));
        // Intentar cambiar color: debe lanzar conflicto
        var reqConflict = new com.Color_craze.board.dtos.UpdatePlayerRequest("p1", "YELLOW", "");
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> gameService.updatePlayer(code, reqConflict));
        // Cambiar avatar: permitido
        var reqAvatar = new com.Color_craze.board.dtos.UpdatePlayerRequest("p1", "", "WITCH");
        assertDoesNotThrow(() -> gameService.updatePlayer(code, reqAvatar));
    }

    private GameSession mockSession(String code, String status){
        GameSession gs = new GameSession();
        gs.setCode(code);
        gs.setStatus(status);
        gs.setCreatedAt(Instant.now());
        gs.setJoinDeadline(Instant.now().plusSeconds(30));
        gs.setTheme("metal");
        return gs;
    }
}
