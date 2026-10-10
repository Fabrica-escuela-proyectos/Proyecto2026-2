package com.codefactory.reservas_backend.reservation.domain;

/**
 * La reserva no se puede cancelar en su estado actual (ya cancelada o completada) o está
 * fuera del plazo de cancelación. Mapea a 409.
 */
public class BookingNotCancellableException extends RuntimeException {
    public BookingNotCancellableException(String message) {
        super(message);
    }
}
