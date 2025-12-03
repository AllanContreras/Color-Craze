package com.Color_craze.auth.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.Color_craze.auth.dtos.AuthResponse;
import com.Color_craze.auth.models.AuthUser;
import com.Color_craze.auth.repositories.AuthRepository;
import com.Color_craze.configs.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataAccessException;

public class AuthServiceGuestTokenTest {
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
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("t");
        when(jwtService.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("r");
        authService = new AuthService(authRepository, jwtService, authenticationManager, passwordEncoder);
    }

    @Test
    void createGuestToken_noPersist(){
        // persistGuest=false by default; repository not used
        AuthResponse resp = authService.createGuestToken();
        assertNotNull(resp.userData().id());
        assertTrue(resp.userData().email().startsWith("guest_"));
        assertEquals("t", resp.token());
        assertEquals("r", resp.refreshToken());
        verify(authRepository, never()).save(any());
    }

    @Test
    void createGuestToken_persist_enabled_but_saveThrows(){
        // Enable persistence via reflection (since field is @Value)
        try {
            var f = AuthService.class.getDeclaredField("persistGuest");
            f.setAccessible(true);
            f.setBoolean(authService, true);
        } catch (Exception e) { fail(e); }

        when(authRepository.save(any(AuthUser.class))).thenThrow(mock(DataAccessException.class));

        AuthResponse resp = authService.createGuestToken();
        assertNotNull(resp.userData().id());
        assertTrue(resp.userData().email().startsWith("guest_"));
        // Despite the exception, tokens should be generated
        assertEquals("t", resp.token());
        assertEquals("r", resp.refreshToken());
    }
}
