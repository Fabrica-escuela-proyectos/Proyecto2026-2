package com.codefactory.reservas_backend.provider.domain;

/**
 * HU-06: el proveedor solicitado (por id, o "yo mismo" vía /providers/me)
 * no existe. Mapea a 404 (errores-api-sprint-1.md sección 7).
 */
public class ProviderNotFoundException extends RuntimeException {
    public ProviderNotFoundException(String message) {
        super(message);
    }
}
