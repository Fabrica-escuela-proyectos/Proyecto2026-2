package com.codefactory.reservas_backend.identity.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Utilidad de generación y validación de JWT, implementando la decisión de
 * ADR-002-autenticacion-y-sesiones.md: token firmado, duración máxima de
 * 1 hora, con un identificador propio (claim {@code jti}) para poder
 * cruzarlo contra identity.domain.Session y revocarlo.
 *
 * IMPORTANTE — alcance real en Sprint 1: esta clase es un insumo técnico
 * para HU02 (Inicio de sesión), que no es tarea individual de Simon. No
 * está conectada a ningún filtro de Spring Security todavía (ver
 * SecurityConfig); nadie la invoca en este sprint. Se deja lista y
 * probada de forma aislada para que HU02 no tenga que resolver la
 * mecánica de firmado desde cero.
 *
 * Pendiente de validar con el equipo (ver docs/matriz-actualizaciones.md):
 * algoritmo simétrico (HMAC) elegido por simplicidad para un equipo
 * pequeño; ADR-002 sección 9 no descarta explícitamente una clave
 * asimétrica, así que si el equipo la prefiere, solo cambia esta clase.
 */
@Component
public class JwtTokenProvider {

    private static final Duration TOKEN_DURATION = Duration.ofHours(1);

    private final SecretKey signingKey;

    public JwtTokenProvider(@Value("${security.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Genera un token firmado para el sujeto dado (normalmente el id del
     * usuario), con un {@code jti} nuevo que HU02 puede usar para crear el
     * registro correspondiente en identity.domain.Session.
     *
     * @return el token y su jti, para que quien lo invoque decida cómo
     *         persistir la sesión.
     */
    public IssuedToken issueToken(String subject) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(TOKEN_DURATION);
        String tokenId = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .subject(subject)
                .id(tokenId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new IssuedToken(token, tokenId, expiresAt);
    }

    /**
     * Valida la firma y expiración del token y devuelve sus claims.
     * No consulta identity.domain.Session (revocación); eso es
     * responsabilidad de quien implemente el filtro de autenticación en
     * HU02, combinando este resultado con SessionRepository.
     */
    public Claims parseAndValidate(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record IssuedToken(String token, String tokenId, Instant expiresAt) {
    }
}
