package com.codefactory.reservas_backend.resource.domain;

/** HU-21: el cierre no existe o no es de ese recurso → 404. */
public class ClosureNotFoundException extends RuntimeException {
    public ClosureNotFoundException(String message) {
        super(message);
    }
}
