package com.Color_craze.auth.services;

import com.Color_craze.auth.dtos.AuthResponse;
import com.Color_craze.auth.dtos.LoginRequest;
import com.Color_craze.auth.dtos.RegisterRequest;
import com.Color_craze.auth.models.AuthUser;
import com.Color_craze.auth.repositories.AuthRepository;
import com.Color_craze.configs.CustomUserDetails;
import io.jsonwebtoken.SignatureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AuthServiceTest {

    private AuthRepository authRepository;
    private JwtService jwtService;
    private AuthenticationManager authenticationManager;
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setup() {
        authRepository = mock(AuthRepository.class);
        jwtService = mock(JwtService.class);
        authenticationManager = mock(AuthenticationManager.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authService = new AuthService(authRepository, jwtService, authenticationManager, passwordEncoder);
    }

    @Test
    void login_success_generates_tokens() {
        AuthUser user = AuthUser.builder().id("u1").email("a@b.com").nickname("nick").password("x").build();
        when(authRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        doNothing().when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("tok");
        when(jwtService.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("rtok");

        AuthResponse resp = authService.login(new LoginRequest("a@b.com", "pass"));
        assertEquals("tok", resp.token());
        assertEquals("rtok", resp.refreshToken());
        assertEquals("a@b.com", resp.user().email());
    }

    @Test
    void login_bad_credentials_throws() {
        when(authRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());
        doAnswer(inv -> { throw new BadCredentialsException("bad"); }).when(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));
        assertThrows(BadCredentialsException.class, () -> authService.login(new LoginRequest("a@b.com", "x")));
    }

    @Test
    void refreshToken_invalid_signature_throws() {
        when(jwtService.extractUsername("bad")).thenAnswer(inv -> { throw new SignatureException("bad"); });
        assertThrows(SignatureException.class, () -> authService.refreshToken("bad"));
    }

    @Test
    void refreshToken_valid_returns_new_tokens() {
        AuthUser user = AuthUser.builder().id("u1").email("a@b.com").nickname("n").password("x").build();
        when(jwtService.extractUsername("rt")).thenReturn("a@b.com");
        when(authRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(eq("rt"), any(CustomUserDetails.class))).thenReturn(true);
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("newTok");
        when(jwtService.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("newRT");

        AuthResponse resp = authService.refreshToken("rt");
        assertEquals("newTok", resp.token());
        assertEquals("newRT", resp.refreshToken());
    }

    @Test
    void createGuestToken_generates_tokens_even_without_persist() {
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("gtok");
        when(jwtService.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("grtok");
        when(authRepository.save(any(AuthUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse resp = authService.createGuestToken();
        assertNotNull(resp.user().id());
        assertTrue(resp.user().email().startsWith("guest_"));
        assertEquals("gtok", resp.token());
        assertEquals("grtok", resp.refreshToken());
    }

    @Test
    void register_non_persist_fallback_on_data_access_exception() {
        RegisterRequest req = new RegisterRequest("x@y.com", "pwd", "nick");
        when(passwordEncoder.encode("pwd")).thenReturn("HASH");
        when(authRepository.existsByEmail("x@y.com")).thenReturn(false);
        when(authRepository.save(any(AuthUser.class))).thenAnswer(inv -> {
            throw new MockitoException("mongo down");
        });
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("tok");
        when(jwtService.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("rtok");

        AuthResponse resp = authService.register(req);
        assertEquals("x@y.com", resp.user().email());
        assertEquals("tok", resp.token());
        assertEquals("rtok", resp.refreshToken());
    }

    static class MockitoException extends DataAccessException {
        public MockitoException(String msg) { super(msg); }
    }
}
