package com.codefactory.reservas_backend.service.domain;

/**
 * HU-20: el servicio no existe, fue eliminado, está inactivo o su proveedor tiene
 * la cuenta inactiva. Mapea a 404 con un único mensaje para todos los casos.
 */
public class ServiceNotAvailableException extends RuntimeException {
    public ServiceNotAvailableException(String message) {
        super(message);
    }
}
