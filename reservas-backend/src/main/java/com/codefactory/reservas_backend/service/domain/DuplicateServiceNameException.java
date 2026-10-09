package com.codefactory.reservas_backend.service.domain;

/** HU-09: ya existe un servicio con ese nombre en el mismo negocio (409). */
public class DuplicateServiceNameException extends RuntimeException {
    public DuplicateServiceNameException(String message) {
        super(message);
    }
}
