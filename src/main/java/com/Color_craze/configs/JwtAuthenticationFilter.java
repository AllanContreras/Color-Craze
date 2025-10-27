package com.Color_craze.configs;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.Color_craze.auth.services.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Filtro JWT para autenticar peticiones usando usuarios almacenados en MongoDB.
 */
@Component
@ConditionalOnProperty(name = "spring.data.mongodb.uri", matchIfMissing = false)
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Skip JWT auth for public endpoints and preflight
        String path = request.getServletPath();
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) return true; // CORS preflight
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/games")
                || path.startsWith("/color-craze/ws")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-resources");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No hay token, continuar la cadena de filtros
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        final String username;
        try {
            username = jwtService.extractUsername(jwt);
        } catch (ExpiredJwtException e) {
            // Token expirado: no autenticamos, pero permitimos que la petición continúe.
            // Esto evita que una petición con token expirado falle con excepción y cause
            // un error de red en el frontend.
            filterChain.doFilter(request, response);
            return;
        } catch (JwtException e) {
            // Token inválido por cualquier otra razón: continuar sin autenticar
            filterChain.doFilter(request, response);
            return;
        }

        // Si tenemos username y no hay autenticación previa en el contexto
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Recupera el usuario desde Mongo a través de UserDetailsServiceImpl
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            // Valida el token
            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
