package com.Color_craze.auth.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.Color_craze.auth.dtos.AuthResponse;
import com.Color_craze.auth.dtos.RegisterRequest;
import com.Color_craze.auth.models.AuthUser;
import com.Color_craze.auth.repositories.AuthRepository;
import com.Color_craze.configs.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataAccessException;

public class AuthServiceRegisterTest {

    private AuthRepository authRepository;
    private JwtService jwtService;
    private AuthenticationManager authenticationManager;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setup(){
        authRepository = mock(AuthRepository.class);
        jwtService = mock(JwtService.class);
        authenticationManager = mock(AuthenticationManager.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hashed:" + inv.getArgument(0));
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("t");
        when(jwtService.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("r");
        authService = new AuthService(authRepository, jwtService, authenticationManager, passwordEncoder);
    }

    @Test
    void register_nonPersistent_fallbackOnDataAccess(){
        // persistAuth=false by default
        RegisterRequest req = new RegisterRequest("user@example.com", "pass", "nick");
        // Simulate repository throws to trigger fallback
        when(authRepository.existsByEmail(req.email())).thenReturn(false);
        when(authRepository.save(any(AuthUser.class))).thenThrow(mock(DataAccessException.class));

        AuthResponse resp = authService.register(req);
        assertNotNull(resp.userData().id());
        assertEquals(req.email(), resp.userData().email());
        assertEquals("t", resp.token());
        assertEquals("r", resp.refreshToken());
    }

    @Test
    void register_nonPersistent_normalSave(){
        RegisterRequest req = new RegisterRequest("user2@example.com", "pass", "nick2");
        when(authRepository.existsByEmail(req.email())).thenReturn(false);
        when(authRepository.save(any(AuthUser.class))).thenAnswer(inv -> {
            AuthUser u = inv.getArgument(0);
            return AuthUser.builder().id("idSaved").email(u.getEmail()).nickname(u.getNickname()).password(u.getPassword()).build();
        });

        AuthResponse resp = authService.register(req);
        assertEquals("idSaved", resp.userData().id());
        assertEquals(req.email(), resp.userData().email());
    }
}
