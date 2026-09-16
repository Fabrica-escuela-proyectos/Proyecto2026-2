package com.codefactory.reservas_backend.identity.infrastructure;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limitador en memoria para el registro de cuentas (clientes y, desde
 * HU-03, proveedores). Cubre el escenario Gherkin "Bloqueo temporal por
 * múltiples intentos de registro" de HU-01: "se detectan muchas solicitudes
 * de registro en poco tiempo desde el mismo origen" -> bloqueo temporal de
 * nuevas solicitudes. Mapea al error 429 / TOO_MANY_REQUESTS de
 * errores-api-sprint-1.md sección 9, relevante también para HU-03 (misma
 * sección, tabla del punto 12).
 *
 * Se reutiliza el mismo bean (misma instancia, mismo contador por IP) desde
 * provider.application.ProviderRegistrationService en vez de duplicar este
 * componente: es una utilidad técnica sin datos de dominio, no una
 * dependencia hacia las entidades/repositorios internos de Identity que
 * ADR-003-modularidad-e-interfaces.md busca evitar entre módulos. Además,
 * compartir el contador entre /users y /providers cierra el hueco de que
 * alguien evada el límite de HU-01 simplemente alternando de endpoint.
 *
 * Suficiente para Sprint 1 (una sola instancia). Si el backend se despliega
 * en múltiples instancias, este mecanismo debe migrar a un almacén
 * compartido (p. ej. Redis) — se deja anotado como evolución futura, no
 * como deuda oculta (ver docs/ADR-002-insumos.md).
 */
@Component
public class RegistrationRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, AtomicInteger> attemptsByOrigin = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> blockedUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> windowStartedAt = new ConcurrentHashMap<>();

    public boolean isBlocked(String originIp) {
        Instant until = blockedUntil.get(originIp);
        return until != null && Instant.now().isBefore(until);
    }

    public void registerAttempt(String originIp) {
        Instant now = Instant.now();
        windowStartedAt.compute(originIp, (key, start) -> {
            if (start == null || Duration.between(start, now).compareTo(WINDOW) > 0) {
                attemptsByOrigin.put(originIp, new AtomicInteger(0));
                return now;
            }
            return start;
        });

        int attempts = attemptsByOrigin
                .computeIfAbsent(originIp, key -> new AtomicInteger(0))
                .incrementAndGet();

        if (attempts > MAX_ATTEMPTS) {
            blockedUntil.put(originIp, now.plus(BLOCK_DURATION));
        }
    }
}
