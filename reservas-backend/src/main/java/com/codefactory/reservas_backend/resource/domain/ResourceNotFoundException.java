package com.codefactory.reservas_backend.resource.domain;

/** El recurso indicado en la ruta no existe (404). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
