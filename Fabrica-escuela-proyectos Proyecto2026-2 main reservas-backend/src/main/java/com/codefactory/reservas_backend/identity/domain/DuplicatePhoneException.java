package com.codefactory.reservas_backend.identity.domain;

/**
 * Viola la regla de dominio "el número de celular debe ser único"
 * (HU-01, escenario "Registro con número de celular ya registrado en otra
 * cuenta").
 */
public class DuplicatePhoneException extends RuntimeException {
    public DuplicatePhoneException(String message) {
        super(message);
    }
}
