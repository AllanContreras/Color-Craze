package com.Color_craze.board.services;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import com.Color_craze.board.models.Box;
// imports cleaned: Player and ColorStatus no longer used in this service
import com.Color_craze.utils.enums.PlayerMove;
import com.Color_craze.utils.enums.ColorStatus;
import com.Color_craze.board.models.Player;
import com.Color_craze.board.dtos.Responses.MoveResult;
import com.Color_craze.board.models.Board;

@Service
public class BoardService {
    private final ConcurrentMap<String, Board> boards = new ConcurrentHashMap<>();

    public BoardService() {
        // no-op: boards are created per-game on demand
    }

    public Board getOrCreateBoard(String gameCode) {
        return boards.computeIfAbsent(gameCode, k -> new Board());
    }

    public Box getBlock(String gameCode, int row, int col) {
        Board b = getOrCreateBoard(gameCode);
        return b.getGrid()[row][col];
    }

    public void setBlock(String gameCode, int row, int col, Box block) {
        Board b = getOrCreateBoard(gameCode);
        b.getGrid()[row][col] = block;
    }

    public MoveResult movePlayer(String gameCode, String playerId, PlayerMove playerMove) {
        Board b = getOrCreateBoard(gameCode);
        UUID uuid = UUID.fromString(playerId);
        return b.movePlayer(uuid, playerMove);
    }

    public void applyPlatformStates(String gameCode, java.util.List<com.Color_craze.board.models.GameSession.PlatformState> states) {
        Board b = getOrCreateBoard(gameCode);
        for (var s : states) {
            b.getGrid()[s.row][s.col] = new com.Color_craze.board.models.Platform(s.color);
        }
    }

    public java.util.List<com.Color_craze.board.models.GameSession.PlatformState> exportPlatformStates(String gameCode) {
        Board b = getOrCreateBoard(gameCode);
        java.util.List<com.Color_craze.board.models.GameSession.PlatformState> out = new java.util.ArrayList<>();
        for (int r = 0; r < 15; r++) {
            for (int c = 0; c < 31; c++) {
                var box = b.getGrid()[r][c];
                if (box instanceof com.Color_craze.board.models.Platform p) {
                    out.add(new com.Color_craze.board.models.GameSession.PlatformState(r, c, p.getColor()));
                }
            }
        }
        return out;
    }

    public void ensurePlayerOnBoard(String gameCode, String playerId, ColorStatus color) {
        Board b = getOrCreateBoard(gameCode);
        UUID uuid = UUID.fromString(playerId);
        if (!b.getPlayers().containsKey(uuid)) {
            Player p = new Player(uuid, color);
            b.addPlayer(p);
        }
    }
}