package com.codefactory.reservas_backend.service.domain;

/** El servicio indicado en la ruta no existe (404). */
public class ServiceNotFoundException extends RuntimeException {
    public ServiceNotFoundException(String message) {
        super(message);
    }
}
