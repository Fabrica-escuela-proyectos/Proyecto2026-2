package com.codefactory.reservas_backend.reservation.domain;

/**
 * HU-22: la solicitud de reserva es inválida (fecha o rango de horas, antelación
 * mínima, duración distinta de la del servicio, recurso no asignado). Mapea a 400.
 */
public class InvalidBookingException extends RuntimeException {
    public InvalidBookingException(String message) {
        super(message);
    }
}
