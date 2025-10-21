package com.Color_craze.board.dtos.Responses;

import java.util.List;

public record MoveResult(
    int newRow,
    int newCol,
    List<PlatformUpdate> platforms,
    List<PlayerUpdate> affectedPlayers,
    boolean success
) {}
