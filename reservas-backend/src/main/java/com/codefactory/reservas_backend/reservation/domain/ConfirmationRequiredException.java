package com.codefactory.reservas_backend.reservation.domain;

/**
 * HU-16: la operación tiene consecuencias (cancelar reservas futuras) y el proveedor todavía no las
 * confirmó. Mapea a 409 con el código {@code CONFIRMATION_REQUIRED} y la cantidad afectada; no se
 * cambió nada. Patrón reutilizable para otras operaciones que pidan confirmación.
 */
public class ConfirmationRequiredException extends RuntimeException {

    private final long affectedBookings;

    public ConfirmationRequiredException(String message, long affectedBookings) {
        super(message);
        this.affectedBookings = affectedBookings;
    }

    public long getAffectedBookings() {
        return affectedBookings;
    }
}
