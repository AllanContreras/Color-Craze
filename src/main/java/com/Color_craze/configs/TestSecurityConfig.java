package com.Color_craze.configs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@Profile("test") // Solo activa esta configuración en el perfil 'test'
public class TestSecurityConfig {

    @Bean("testSecurityFilterChain")
    @org.springframework.core.annotation.Order(1)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Este chain solo aplica a rutas de test/actuator. Evita conflicto con el chain principal.
            .securityMatcher(new OrRequestMatcher(
                new RegexRequestMatcher("^/api/test/.*", null),
                new RegexRequestMatcher("^/actuator/.*", null)
            ))
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/test/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().permitAll()
            );
        
        return http.build();
    }

    // Se omite la definición de CorsConfigurationSource aquí para no duplicar el bean
}