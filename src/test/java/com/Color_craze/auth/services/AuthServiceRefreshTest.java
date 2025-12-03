package com.Color_craze.auth.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.Color_craze.auth.dtos.AuthResponse;
import com.Color_craze.auth.models.AuthUser;
import com.Color_craze.auth.repositories.AuthRepository;
import com.Color_craze.configs.CustomUserDetails;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

public class AuthServiceRefreshTest {

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
        authService = new AuthService(authRepository, jwtService, authenticationManager, passwordEncoder);
    }

    @Test
    void refreshToken_happyPath_generatesNewTokens(){
        String email = "user@example.com";
        String refresh = "refreshToken";
        AuthUser user = AuthUser.builder().id("id1").email(email).nickname("nick").password("p").build();

        when(jwtService.extractUsername(refresh)).thenReturn(email);
        when(authRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(eq(refresh), any(CustomUserDetails.class))).thenReturn(true);
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("newAccess");
        when(jwtService.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("newRefresh");

        AuthResponse resp = authService.refreshToken(refresh);

        assertEquals("newAccess", resp.token());
        assertEquals("newRefresh", resp.refreshToken());
        assertEquals(email, resp.userData().email());
    }

    @Test
    void refreshToken_expired_throwsExpiredJwt(){
        String refresh = "expired";
        when(jwtService.extractUsername(refresh)).thenThrow(new ExpiredJwtException(null, null, "expired"));
        assertThrows(ExpiredJwtException.class, () -> authService.refreshToken(refresh));
    }

    @Test
    void refreshToken_invalidSignature_throwsSignature(){
        String refresh = "invalid";
        when(jwtService.extractUsername(refresh)).thenThrow(new SignatureException("invalid"));
        assertThrows(SignatureException.class, () -> authService.refreshToken(refresh));
    }
}
