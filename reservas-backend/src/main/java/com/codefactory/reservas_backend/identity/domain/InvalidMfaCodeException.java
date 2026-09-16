package com.codefactory.reservas_backend.identity.domain;

/**
 * El código TOTP enviado a POST /api/v1/auth/mfa/activate no coincide con el
 * secreto pendiente del usuario. A diferencia de InvalidCredentialsException
 * (login), aquí el usuario ya está autenticado y activando MFA por su propia
 * cuenta, así que no aplica el mismo principio de "no revelar cuál falló".
 */
public class InvalidMfaCodeException extends RuntimeException {
    public InvalidMfaCodeException(String message) {
        super(message);
    }
}
