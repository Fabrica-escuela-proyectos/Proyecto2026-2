package com.codefactory.reservas_backend.identity.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Limitador en memoria para el registro de cuentas (clientes y, desde
 * HU-03, proveedores). Cubre el escenario Gherkin "Bloqueo temporal por
 * múltiples intentos de registro" de HU-01: "se detectan muchas solicitudes
 * de registro en poco tiempo desde el mismo origen" -> bloqueo temporal de
 * nuevas solicitudes. Mapea al error 429 / TOO_MANY_REQUESTS de
 * errores-api-sprint-1.md sección 9, relevante también para HU-03 (misma
 * sección, tabla del punto 12).
 *
 * Por defecto: 5 intentos por origen en una ventana de 10 minutos; el 6.º
 * intento se rechaza y bloquea el origen 15 minutos. Los valores salen de
 * {@code security.rate-limit.registration.*} para poder relajarlos en el
 * perfil de pruebas de integración (que registra decenas de cuentas desde la
 * misma IP).
 *
 * Se reutiliza el mismo bean (misma instancia, mismo contador por IP) desde
 * provider.application.ProviderRegistrationService en vez de duplicar este
 * componente: es una utilidad técnica sin datos de dominio, no una
 * dependencia hacia las entidades/repositorios internos de Identity que
 * ADR-003-modularidad-e-interfaces.md busca evitar entre módulos. Además,
 * compartir el contador entre /users y /providers cierra el hueco de que
 * alguien evada el límite de HU-01 simplemente alternando de endpoint.
 *
 * La lógica de conteo (atómica) vive en {@link AttemptLimiter}.
 */
@Component
public class RegistrationRateLimiter {

    private final AttemptLimiter limiter;

    public RegistrationRateLimiter(
            @Value("${security.rate-limit.registration.max-attempts:5}") int maxAttempts,
            @Value("${security.rate-limit.registration.window-minutes:10}") long windowMinutes,
            @Value("${security.rate-limit.registration.block-minutes:15}") long blockMinutes) {
        this.limiter = new AttemptLimiter(maxAttempts, Duration.ofMinutes(windowMinutes), Duration.ofMinutes(blockMinutes));
    }

    /**
     * Cuenta el intento de registro del origen. {@code true} = se permite
     * (los primeros 5); {@code false} = el origen está bloqueado (el 6.º
     * intento y los siguientes hasta que termine el bloqueo).
     */
    public boolean tryAcquire(String originIp) {
        return limiter.tryAcquire(originIp);
    }
}
