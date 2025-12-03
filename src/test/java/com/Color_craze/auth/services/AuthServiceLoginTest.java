package com.Color_craze.auth.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.Color_craze.auth.dtos.AuthResponse;
import com.Color_craze.auth.dtos.LoginRequest;
import com.Color_craze.auth.models.AuthUser;
import com.Color_craze.auth.repositories.AuthRepository;
import com.Color_craze.configs.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

public class AuthServiceLoginTest {
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
    void login_validCredentials_returnsTokens(){
        LoginRequest req = new LoginRequest("user@example.com", "pass");
        AuthUser user = AuthUser.builder().id("id1").email(req.email()).nickname("nick").password("hashed").build();

        when(authRepository.findByEmail(req.email())).thenReturn(Optional.of(user));
        // authenticationManager.authenticate succeeds by default (no exception)
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("t");
        when(jwtService.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("r");

        AuthResponse resp = authService.login(req);
        assertEquals("t", resp.token());
        assertEquals("r", resp.refreshToken());
        assertEquals(req.email(), resp.userData().email());
    }

    @Test
    void login_invalidCredentials_throwsBadCredentials(){
        LoginRequest req = new LoginRequest("user@example.com", "bad");
        // make authentication throw
        doThrow(new BadCredentialsException("bad")).when(authenticationManager)
            .authenticate(any(UsernamePasswordAuthenticationToken.class));
        when(authRepository.findByEmail(req.email())).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.login(req));
    }
}
