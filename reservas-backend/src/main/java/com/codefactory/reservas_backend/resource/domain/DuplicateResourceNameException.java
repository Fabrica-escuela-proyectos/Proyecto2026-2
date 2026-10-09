package com.codefactory.reservas_backend.resource.domain;

/** HU-14: ya existe un recurso con ese nombre en el mismo negocio (409). */
public class DuplicateResourceNameException extends RuntimeException {
    public DuplicateResourceNameException(String message) {
        super(message);
    }
}
