package com.codefactory.reservas_backend.reservation.domain;

/**
 * HU-22: el horario pedido no está disponible (ya hay una reserva activa o cae
 * fuera del horario del recurso). Mapea a 409.
 */
public class SlotNotAvailableException extends RuntimeException {
    public SlotNotAvailableException(String message) {
        super(message);
    }
}
