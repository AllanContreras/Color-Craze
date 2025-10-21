package com.Color_craze.board.dtos;

import java.util.List;

public record GameInfoResponse(
    String code,
    String status,
    List<PlayerInfo> players
) {
    public static record PlayerInfo(String playerId, String nickname, String color, int score) {}
}
