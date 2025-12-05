package com.Color_craze.board.dtos;

public record GameInfoLiteResponse(
    String code,
    String status,
    Long joinDeadlineMs,
    int playerCount,
    String theme
) {}
