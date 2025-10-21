package com.Color_craze.board.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.Color_craze.board.dtos.Responses.MoveResult;
import com.Color_craze.board.dtos.Responses.PlatformUpdate;
import com.Color_craze.board.dtos.Responses.PlayerUpdate;
import com.Color_craze.utils.enums.ColorStatus;
import com.Color_craze.utils.enums.PlayerMove;

public class Board {

    private static final int ROWS = 15;
    private static final int COLS = 31;

    private final Box[][] grid;
    private final Map<UUID, Player> players;

    private final Object gridLock = new Object();
    private final List<UUID> lockQueue = new ArrayList<>();

    public Board() {
        this.grid = new Box[ROWS][COLS];
        this.players = new HashMap<>();
        initBoard();
    }

    private void initBoard() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                grid[r][c] = new Box(ColorStatus.WHITE);
            }
        }
    }

    public void addPlayer(Player player) {
        players.put(player.getId(), player);
        grid[player.getRow()][player.getCol()] = player;
    }

    public void removePlayer(UUID playerId) {
        Player player = players.remove(playerId);
        if (player != null) {
            grid[player.getRow()][player.getCol()] = new Box(ColorStatus.WHITE);
        }
    }

    public MoveResult movePlayer(UUID playerId, PlayerMove playerMove) {
        Player player = players.get(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Player not found: " + playerId);
        }

        int currentRow = player.getRow();
        int currentCol = player.getCol();
        int newRow = currentRow;
        int newCol = currentCol;

        switch (playerMove) {
            case RIGHT -> newCol++;
            case LEFT  -> newCol--;
            case UP    -> newRow--;
            case DOWN  -> newRow++;
        }

        if (newRow < 0 || newRow >= ROWS || newCol < 0 || newCol >= COLS) {
            return new MoveResult(currentRow, currentCol, List.of(), List.of(), false);
        }

        Box destination = grid[newRow][newCol];
        if (destination instanceof Platform || destination instanceof Player) {
            return new MoveResult(currentRow, currentCol, List.of(), List.of(), false);
        }

        synchronized (getGridLock(playerId)) {
            while (!canAcquireLock(playerId)) {
                try {
                    getGridLock(playerId).wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new MoveResult(currentRow, currentCol, List.of(), List.of(), false);
                }
            }

            try {
                grid[currentRow][currentCol] = new Box(ColorStatus.WHITE);
                player.setRow(newRow);
                player.setCol(newCol);
                grid[newRow][newCol] = player;

                List<PlayerUpdate> affectedPlayers = new ArrayList<>();
                List<PlatformUpdate> updatedPlatforms = updateAdjacentPlatforms(newRow, newCol, player.getColor(), affectedPlayers);
                return new MoveResult(newRow, newCol, updatedPlatforms, affectedPlayers, true);
            } finally {
                releaseLock(playerId);
                getGridLock(playerId).notifyAll();
            }
        }
    }


    private List<PlatformUpdate> updateAdjacentPlatforms(int row, int col, ColorStatus playerColor, List<PlayerUpdate> affectedPlayers) {
        int[][] directions = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}
        };

        List<PlatformUpdate> updates = new ArrayList<>();

        for (int[] dir : directions) {
            int r = row + dir[0];
            int c = col + dir[1];

            if (r >= 0 && r < ROWS && c >= 0 && c < COLS) {
                Box box = grid[r][c];
                if (box instanceof Platform platform) {
                    updatePlatformAndScores(platform, playerColor, affectedPlayers);
                    updates.add(new PlatformUpdate(r, c, playerColor));
                }
            }
        }

        return updates;
    }


    private void updatePlatformAndScores(Platform platform, ColorStatus newColor, List<PlayerUpdate> affectedPlayers) {
        ColorStatus previousColor = platform.getColor();

        if (previousColor == newColor) {
            return;
        }

        Player paintingPlayer = findPlayerByColor(newColor);
        if (paintingPlayer != null) {
            paintingPlayer.setScore(paintingPlayer.getScore() + 1);
            affectedPlayers.add(new PlayerUpdate(paintingPlayer.getId(), paintingPlayer.getColor(), paintingPlayer.getScore()));
        }

        if (previousColor != ColorStatus.WHITE && previousColor != newColor) {
            Player previousPlayer = findPlayerByColor(previousColor);
            if (previousPlayer != null && previousPlayer.getScore() > 0) {
                previousPlayer.setScore(previousPlayer.getScore() - 1);
                affectedPlayers.add(new PlayerUpdate(previousPlayer.getId(), previousPlayer.getColor(), previousPlayer.getScore()));
            }
        }

        platform.setColor(newColor);
    }


    private Player findPlayerByColor(ColorStatus color) {
        return players.values().stream()
                .filter(p -> p.getColor() == color)
                .findFirst()
                .orElse(null);
    }


    public Box[][] getGrid() {
        return grid;
    }

    public Map<UUID, Player> getPlayers() {
        return players;
    }

    //Herramientas para el bloqueo 

    private Object getGridLock(UUID playerId) {
        synchronized (gridLock) {
            if (!lockQueue.contains(playerId)) {
                lockQueue.add(playerId);
                lockQueue.sort(UUID::compareTo);
            }
            return gridLock;
        }
    }

    private boolean canAcquireLock(UUID playerId) {
        synchronized (gridLock) {
            return !lockQueue.isEmpty() && lockQueue.get(0).equals(playerId);
        }
    }

    private void releaseLock(UUID playerId) {
        synchronized (gridLock) {
            lockQueue.remove(playerId);
        }
    }

}