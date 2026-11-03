package com.codefactory.reservas_backend.resource.domain;

/** HU-21: periodo de cierre con formato u orden inválido, en el pasado o demasiado largo → 400. */
public class InvalidClosureException extends RuntimeException {
    public InvalidClosureException(String message) {
        super(message);
    }
}
