package com.Color_craze.auth.services;

import java.util.Date;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Service for handling JWT (JSON Web Token) operations such as
 * generating, validating, and extracting claims from tokens.
 */
@Service
public class JwtService {

    // 🔑 Llave secreta (usa al menos 256 bits). Guarda esto en application.properties en producción.
    private final String secretKey =
        "EPRiC0Bt0P2KcBRRWqVKhEWzModEtI6Q4K05RWuLgVQV4Xw92Ulk9kHPmQVjiRW5c9XtLNm4lgNoridiLgvZpgC5";

    // ⏳ Expiración de tokens: 30 min acceso, 1 hora refresh.
    private final long jwtExpiration = 1800000;      // 30 min
    private final long refreshExpiration = 3600000;  // 60 min

    /**
     * Extrae el email/username del token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Genera un token de acceso normal.
     */
    public String generateToken(UserDetails userDetails) {
        return buildToken(Map.of(), userDetails, jwtExpiration);
    }

    /**
     * Genera un refresh token e incluye el rol en los claims.
     */
    public String generateRefreshToken(UserDetails userDetails) {
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("USER");
        Map<String, Object> claims = Map.of("role", role);
        return buildToken(claims, userDetails, refreshExpiration);
    }

    /**
     * Verifica si el token es válido: username coincide y no expiró.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            if (userDetails == null) return false;
            final Claims claims = extractAllClaims(token);
            final String username = claims.getSubject();
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extrae un claim específico del token.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    /**
     * Construye un token JWT firmado con HS256.
     */
    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Parsea y devuelve todos los claims de un token.
     */
    private Claims extractAllClaims(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token);

        if (!"HS256".equals(jws.getHeader().getAlgorithm())) {
            throw new JwtException("Invalid JWT algorithm. Expected HS256");
        }
        return jws.getPayload();
    }

    /**
     * Verifica si el token ha expirado.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extrae la fecha de expiración.
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Obtiene la llave secreta para firmar y validar tokens.
     */
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
}
