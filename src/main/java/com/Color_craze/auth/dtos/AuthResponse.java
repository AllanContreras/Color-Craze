package com.Color_craze.auth.dtos;

public record AuthResponse(String token, String refreshToken, UserDetailsResponse userData) {
}