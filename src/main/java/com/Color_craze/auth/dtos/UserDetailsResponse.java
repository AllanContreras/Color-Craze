package com.Color_craze.auth.dtos;

/**
 * Respuesta con los datos del usuario autenticado.
 * En MongoDB, el id se maneja como String.
 */
public record UserDetailsResponse(
        String id,
        String email,
        String nickname) {
}
