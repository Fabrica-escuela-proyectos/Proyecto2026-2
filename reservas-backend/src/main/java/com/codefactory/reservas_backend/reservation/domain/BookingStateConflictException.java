package com.codefactory.reservas_backend.reservation.domain;

/** HU-27: la reserva no está en un estado que permita la operación (cancelada, ya completada o sin terminar) → 409. */
public class BookingStateConflictException extends RuntimeException {
    public BookingStateConflictException(String message) {
        super(message);
    }
}
