package com.Color_craze.board.dtos;

import java.util.List;

public record GameInfoResponse(
    String code,
    String status,
    Long joinDeadlineMs,
    List<PlayerInfo> players,
    Long startedAtMs,
    Long durationMs,
    List<PlayerPos> playerPositions,
    ArenaConfig arena
) {
    public static record PlayerInfo(String playerId, String nickname, String color, String avatar, int score) {}
    public static record PlayerPos(String playerId, int row, int col, String color) {}
    public static record ArenaConfig(double width, double height, List<ArenaPlatform> platforms) {}
    public static record ArenaPlatform(double x, double y, double width, double height, int cells) {}
}
