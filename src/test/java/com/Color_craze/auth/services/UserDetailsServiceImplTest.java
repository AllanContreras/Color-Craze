package com.Color_craze.auth.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.Color_craze.auth.models.AuthUser;
import com.Color_craze.auth.repositories.AuthRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

public class UserDetailsServiceImplTest {

    private AuthRepository authRepository;
    private UserDetailsServiceImpl service;

    @BeforeEach
    void setup(){
        authRepository = mock(AuthRepository.class);
        service = new UserDetailsServiceImpl(authRepository);
    }

    @Test
    void loadUser_found_returnsDetails(){
        AuthUser user = AuthUser.builder().id("id1").email("u@example.com").nickname("n").password("p").build();
        when(authRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        var details = service.loadUserByUsername("u@example.com");
        assertEquals("u@example.com", details.getUsername());
    }

    @Test
    void loadUser_notFound_throws(){
        when(authRepository.findByEmail("x@example.com")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("x@example.com"));
    }
}
