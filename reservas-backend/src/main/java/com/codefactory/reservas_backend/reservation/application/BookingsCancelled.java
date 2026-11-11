package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evento de dominio: una decisión que no tomó el cliente (proveedor, desactivación, cierre o eliminación de una
 * cuenta) canceló reservas confirmadas. Lo publica el módulo de reservas dentro de su transacción; quien quiera
 * reaccionar (hoy el módulo {@code notification}, que avisa por correo a cada cliente) lo escucha sin que
 * {@code reservation} lo conozca (ADR-007, ADR-009). Lleva copias simples, nunca la entidad; el origen va como
 * texto ({@code PROVEEDOR}, {@code SERVICIO_NO_DISPONIBLE}...) para que nadie importe el dominio de reservas.
 */
public record BookingsCancelled(String origin, String reason, List<CancelledBooking> bookings) {

    /** Lo que el aviso necesita de cada reserva cancelada. {@code clientEmail} es nulo si la cuenta ya no existe. */
    public record CancelledBooking(UUID bookingId, String clientEmail, String clientName, String businessName,
                                   String serviceName, String resourceName, Instant startAt) {

        static CancelledBooking of(Booking b) {
            return new CancelledBooking(b.getId(), b.getClientEmail(), b.getClientName(), b.getBusinessName(),
                    b.getServiceName(), b.getResourceName(), b.getStartAt());
        }
    }

    static BookingsCancelled of(CancelOrigin origin, String reason, List<Booking> bookings) {
        return new BookingsCancelled(origin.name(), reason, bookings.stream().map(CancelledBooking::of).toList());
    }
}
