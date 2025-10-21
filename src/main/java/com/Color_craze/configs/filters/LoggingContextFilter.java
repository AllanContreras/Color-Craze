package com.Color_craze.configs.filters;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro para asignar y propagar un correlationId en cada petición HTTP.
 * Independiente de la base de datos (compatible con MongoDB o cualquier otra).
 * El correlationId permite rastrear peticiones entre logs y servicios.
 */
@Component
public class LoggingContextFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID = "correlationId";
    private static final String HEADER_NAME = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Obtiene el correlationId del encabezado o genera uno nuevo
        String id = request.getHeader(HEADER_NAME);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }

        // Inserta el correlationId en el MDC (Mapped Diagnostic Context) para los logs
        MDC.put(CORRELATION_ID, id);

        // También propaga el correlationId en la respuesta
        response.setHeader(HEADER_NAME, id);

        try {
            System.out.println("[DEBUG] CorrelationId asignado: " + id);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear(); // Limpia el contexto para evitar fugas entre peticiones
        }
    }
}
