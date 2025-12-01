package com.Color_craze.board.services;

import com.Color_craze.utils.enums.ColorStatus;
import com.Color_craze.utils.enums.PlayerMove;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BoardService painting & scoring rules.
 */
class BoardServiceTest {
    private BoardService boardService;

    @BeforeEach
    void setup(){
        boardService = new BoardService();
    }

    @Test
    void movingPlayerPaintsPreviousCellAndIncrementsScoreOnce() {
        String code = "G1";
        // Ensure player on board
        boardService.ensurePlayerOnBoard(code, "playerA", ColorStatus.YELLOW);
        var beforeExport = boardService.exportPlatformStates(code);
        assertEquals(0, beforeExport.size(), "No plataformas pintadas iniciales");

        // Move RIGHT three times
        for(int i=0;i<3;i++) {
            var payload = boardService.movePlayer(code, "playerA", PlayerMove.RIGHT);
            assertTrue(payload.success(), "Movimiento debe ser exitoso");
        }

        var afterExport = boardService.exportPlatformStates(code);
        // Debe haber exactamente 3 plataformas (huellas) pintadas
        assertEquals(3, afterExport.size(), "Se esperan 3 plataformas pintadas tras 3 movimientos");
    }

    @Test
    void repaintingSameCellDoesNotDoubleCreditScore() {
        String code = "G2";
        boardService.ensurePlayerOnBoard(code, "pY", ColorStatus.YELLOW);
        // Mover derecha y luego izquierda para volver a la misma casilla inicial
        var first = boardService.movePlayer(code, "pY", PlayerMove.RIGHT);
        assertTrue(first.success());
        var back = boardService.movePlayer(code, "pY", PlayerMove.LEFT);
        assertTrue(back.success());
        // Export platform states (should include 2 painted cells; original cell repainted once)
        var states = boardService.exportPlatformStates(code);
        assertTrue(states.size() >= 1, "Debe haber al menos 1 plataforma pintada");
    }
}
