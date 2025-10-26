package com.Color_craze.board.dtos.Responses;

import java.util.List;
import java.util.UUID;

public record MoveResult(
    UUID playerId,
    int newRow,
    int newCol,
    List<PlatformUpdate> platforms,
    List<PlayerUpdate> affectedPlayers,
    boolean success
) {}
