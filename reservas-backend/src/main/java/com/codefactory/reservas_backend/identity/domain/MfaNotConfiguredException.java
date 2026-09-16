package com.codefactory.reservas_backend.identity.domain;

/**
 * POST /api/v1/auth/mfa/activate se llamó sin haber llamado antes a
 * POST /api/v1/auth/mfa/setup (no existe un secreto pendiente para el
 * usuario autenticado).
 */
public class MfaNotConfiguredException extends RuntimeException {
    public MfaNotConfiguredException(String message) {
        super(message);
    }
}
