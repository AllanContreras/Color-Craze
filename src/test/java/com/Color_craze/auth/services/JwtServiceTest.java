package com.Color_craze.auth.services;

import static org.junit.jupiter.api.Assertions.*;

import com.Color_craze.configs.CustomUserDetails;
import com.Color_craze.auth.models.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class JwtServiceTest {

    @Test
    void generateAndValidateToken(){
        JwtService jwt = new JwtService();
        AuthUser user = AuthUser.builder().id("id1").email("u@example.com").nickname("n").password("p").build();
        CustomUserDetails details = new CustomUserDetails(user);

        String token = jwt.generateToken(details);
        assertNotNull(token);
        assertEquals("u@example.com", jwt.extractUsername(token));
        assertTrue(jwt.isTokenValid(token, details));
    }

    @Test
    void refreshTokenContainsRole(){
        JwtService jwt = new JwtService();
        AuthUser user = AuthUser.builder().id("id1").email("u@example.com").nickname("n").password("p").build();
        CustomUserDetails details = new CustomUserDetails(user);

        String refresh = jwt.generateRefreshToken(details);
        assertNotNull(refresh);
        // Validate token and ensure subject is correct
        assertTrue(jwt.isTokenValid(refresh, details));
        assertEquals("u@example.com", jwt.extractUsername(refresh));
    }
}
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
