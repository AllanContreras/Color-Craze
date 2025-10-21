package com.Color_craze.auth.dtos;

public record RegisterRequest(
    String email,
    String password,
    String nickname
) {}
