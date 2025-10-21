package com.Color_craze.board.dtos.Responses;

import java.util.UUID;
import com.Color_craze.utils.enums.ColorStatus;

public record PlayerUpdate(
    UUID playerId,
    ColorStatus color,
    int newScore
) {}
