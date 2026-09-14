package com.codefactory.reservas_backend.identity.domain;

/**
 * Viola la regla de dominio "el correo electrónico debe ser único"
 * (HU-01, escenario "Registro con correo ya existente").
 */
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
