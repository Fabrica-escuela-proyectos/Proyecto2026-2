package com.codefactory.reservas_backend.resource.domain;

/** HU-21: el cierre se cruza con otro ya programado, o se intenta eliminar uno que ya empezó → 409. */
public class ClosureConflictException extends RuntimeException {
    public ClosureConflictException(String message) {
        super(message);
    }
}
