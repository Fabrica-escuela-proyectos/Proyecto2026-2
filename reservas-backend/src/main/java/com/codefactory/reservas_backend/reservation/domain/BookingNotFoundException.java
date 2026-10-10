package com.codefactory.reservas_backend.reservation.domain;

/** La reserva indicada en la ruta no existe (404). */
public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(String message) {
        super(message);
    }
}
