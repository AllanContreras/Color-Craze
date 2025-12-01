package com.Color_craze.board.services;

import com.Color_craze.board.models.GameSession;
import com.Color_craze.utils.enums.ColorStatus;
import com.Color_craze.utils.enums.PlayerMove;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extra tests for BoardService covering:
 * - applyPlatformStates/exportPlatformStates symmetry
 * - ensurePlayerOnBoard stable mapping for non-UUID ids (name-based UUID)
 */
class BoardServiceExtraTest {
    private BoardService boardService;

    @BeforeEach
    void setup(){
        boardService = new BoardService();
    }

    @Test
    void applyPlatformStatesAndExportSymmetry() {
        String code = "BPLAT1";
        var states = List.of(
            new GameSession.PlatformState(0,0, ColorStatus.YELLOW),
            new GameSession.PlatformState(1,2, ColorStatus.PINK),
            new GameSession.PlatformState(14,30, ColorStatus.GREEN)
        );
        boardService.applyPlatformStates(code, states);
        var exported = boardService.exportPlatformStates(code);
        // exported must contain at least those three states
        for (var s : states) {
            assertTrue(exported.stream().anyMatch(e -> e.row == s.row && e.col == s.col && e.color == s.color),
                "Debe exportarse estado de plataforma aplicada en ("+s.row+","+s.col+")");
        }
    }

    @Test
    void ensurePlayerOnBoardStableForNonUuidId() {
        String code = "STABLE1";
        String rawId = "mongoObjectId1234567890"; // not a UUID format
        boardService.ensurePlayerOnBoard(code, rawId, ColorStatus.PURPLE);
        // Move to paint some cells
        boardService.movePlayer(code, rawId, PlayerMove.RIGHT);
        boardService.movePlayer(code, rawId, PlayerMove.DOWN);
        // Call ensure again; should NOT create duplicate player
        boardService.ensurePlayerOnBoard(code, rawId, ColorStatus.PURPLE);
        var board = boardService.getOrCreateBoard(code);
        assertEquals(1, board.getPlayers().size(), "No debe duplicarse jugador con id no-UUID estable");
    }
}
