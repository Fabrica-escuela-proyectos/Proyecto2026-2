package com.codefactory.reservas_backend.service.domain;

/** HU-20: fecha con formato inválido, anterior a hoy o demasiado lejana. Mapea a 400. */
public class InvalidAvailabilityQueryException extends RuntimeException {
    public InvalidAvailabilityQueryException(String message) {
        super(message);
    }
}
