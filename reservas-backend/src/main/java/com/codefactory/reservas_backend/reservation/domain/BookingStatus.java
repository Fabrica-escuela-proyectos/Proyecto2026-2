package com.codefactory.reservas_backend.reservation.domain;

/**
 * Estados de una reserva. Solo las CONFIRMADAS ocupan el horario del recurso
 * (restricción ex_bookings_no_overlap); cancelar o completar lo libera.
 */
public enum BookingStatus {
    CONFIRMADA,
    CANCELADA,
    COMPLETADA
}
