package com.codefactory.reservas_backend.identity.domain;

/**
 * HU-05/HU-06: el usuario objetivo de la operación (consulta, cambio de rol,
 * eliminación) no existe. Mapea a 404 (errores-api-sprint-1.md sección 7).
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
