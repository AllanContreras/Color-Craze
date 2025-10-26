package com.Color_craze.board.arena.dtos;

import java.util.List;
import java.util.Map;

public record ArenaFrame(
    String code,
    List<PlayerPose> players,
    Map<Integer, String[]> paint,
    Map<String, Integer> scores
) {
    public static record PlayerPose(String playerId, double x, double y, boolean onGround) {}
}
