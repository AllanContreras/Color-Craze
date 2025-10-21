package com.Color_craze.board.dtos.Responses;

import com.Color_craze.utils.enums.ColorStatus;

public record PlatformUpdate(int row, int col, ColorStatus color) {}