package com.Color_craze.auth.services;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    @Test
    void generateAndValidateToken() {
        JwtService jwtService = new JwtService();
        UserDetails user = new User("test@example.com", "noop", List.of(new SimpleGrantedAuthority("USER")));

        String token = jwtService.generateToken(user);
        assertNotNull(token);

        String subject = jwtService.extractUsername(token);
        assertEquals("test@example.com", subject);

        assertTrue(jwtService.isTokenValid(token, user));

        // Invalid for different user
        UserDetails other = new User("other@example.com", "noop", List.of(new SimpleGrantedAuthority("USER")));
        assertFalse(jwtService.isTokenValid(token, other));
    }
}
