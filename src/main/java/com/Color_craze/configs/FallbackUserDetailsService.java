package com.Color_craze.configs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fallback UserDetailsService implementation for when MongoDB is not available.
 * This allows the application to start without database dependencies.
 */
@Service
@ConditionalOnMissingBean(name = "userDetailsServiceImpl")
public class FallbackUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // For testing purposes, create a simple test user
        if ("test@example.com".equals(username)) {
            return User.builder()
                    .username("test@example.com")
                    .password("$2a$10$dXJ3SW6G7P4FBdVDqRWiOe.BexY8a3e7gXb4rXv5.RN2.Q.3F9aTC") // "password"
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                    .build();
        }
        
        throw new UsernameNotFoundException("User not found: " + username);
    }
}