package com.codefactory.reservas_backend.identity.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * Control de fuerza bruta para login, código MFA y confirmación de
 * operaciones sensibles (OWASP A07; política de MFA, ADR-004): como máximo 5
 * <b>fallos</b> por clave en 15 minutos; el 5.º fallo bloquea la clave 15
 * minutos y mientras tanto se responde 429 sin siquiera comprobar la
 * contraseña. Un inicio de sesión o una confirmación correctos borran el
 * historial.
 *
 * Las claves se arman con {@link #loginKey} (IP + correo, para que un
 * atacante no bloquee la cuenta de otra persona desde otra IP) y
 * {@link #stepUpKey} (por administrador). El correo se normaliza a
 * minúsculas y se usa tal cual lo envió el cliente, exista o no la cuenta,
 * así el 429 no permite enumerar usuarios.
 *
 * Los valores salen de {@code security.rate-limit.auth.*}.
 */
@Component
public class AuthAttemptLimiter {

    private final AttemptLimiter limiter;

    public AuthAttemptLimiter(
            @Value("${security.rate-limit.auth.max-attempts:5}") int maxAttempts,
            @Value("${security.rate-limit.auth.window-minutes:15}") long windowMinutes,
            @Value("${security.rate-limit.auth.block-minutes:15}") long blockMinutes) {
        this.limiter = new AttemptLimiter(maxAttempts, Duration.ofMinutes(windowMinutes), Duration.ofMinutes(blockMinutes));
    }

    public static String loginKey(String originIp, String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return "login|" + originIp + "|" + normalizedEmail;
    }

    public static String stepUpKey(UUID adminId) {
        return "stepup|" + adminId;
    }

    public boolean isBlocked(String key) {
        return limiter.isBlocked(key);
    }

    public void recordFailure(String key) {
        limiter.recordFailure(key);
    }

    public void reset(String key) {
        limiter.reset(key);
    }
}
