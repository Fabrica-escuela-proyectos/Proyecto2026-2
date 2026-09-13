package com.codefactory.reservas_backend.identity.infrastructure;

/**
 * Representa el rechazo del mecanismo técnico de RegistrationRateLimiter,
 * no una regla del dominio de usuario — por eso vive en infrastructure y
 * no en identity.domain.
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
