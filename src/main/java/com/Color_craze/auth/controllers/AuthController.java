package com.Color_craze.auth.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Color_craze.auth.dtos.AuthResponse;
import com.Color_craze.auth.dtos.LoginRequest;
import com.Color_craze.auth.dtos.RefreshTokenRequest;
import com.Color_craze.auth.services.AuthService;
import com.Color_craze.auth.dtos.RegisterRequest;

import lombok.RequiredArgsConstructor;

/**
 * Controller for handling authentication requests such as login and token refresh.
 * Provides endpoints to authenticate users and issue JWT tokens.
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * Authenticates a user with the provided login credentials.
     *
     * @param request The login request containing email and password.
     * @return ResponseEntity containing AuthResponse with JWT token and user info.
     */
    private final AuthService authService;
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        System.out.println("Entró al método login con " + request);
        try {
            var result = authService.login(request);
            System.out.println("Antes de retornar " + result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.out.println("Error en login: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }


    /**
     * Refreshes a JWT token using a valid refresh token.
     *
     * @param request The request containing the refresh token.
     * @return ResponseEntity containing AuthResponse with a new JWT token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/guest")
    public ResponseEntity<AuthResponse> guestLogin() {
        AuthResponse guestAuth = authService.createGuestToken();
        return ResponseEntity.ok(guestAuth);
    }


}