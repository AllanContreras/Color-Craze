package com.Color_craze.board.dtos;

/**
 * Update current player's selection while in WAITING state.
 * All fields optional; only provided values will be updated.
 */
public record UpdatePlayerRequest(String playerId, String color, String avatar) {}
