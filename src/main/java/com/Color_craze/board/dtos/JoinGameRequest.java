package com.Color_craze.board.dtos;

/**
 * Request to join a game room.
 * color is optional; when provided, it must be one of: YELLOW, PINK, PURPLE, GREEN.
 * avatar is optional; simple string identifier for front-end visuals (e.g., "CAT", "DOG").
 */
public record JoinGameRequest(String playerId, String nickname, String color, String avatar) {}
