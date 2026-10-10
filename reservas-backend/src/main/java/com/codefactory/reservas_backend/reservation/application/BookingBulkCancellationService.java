package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;

import java.util.Collection;
import java.util.UUID;

/**
 * Cancelación masiva de reservas FUTURAS CONFIRMADAS cuando una decisión del sistema o del
 * proveedor las deja sin efecto (HU-16 desactivar un recurso; HU-28 eliminar una cuenta). No aplica
 * la regla de antelación de 1 hora (plan, sección 6) y conserva el historial: las reservas pasadas,
 * las ya canceladas y las completadas no se tocan. Debe llamarse dentro de la transacción de quien
 * la dispara para que todo sea atómico.
 */
public interface BookingBulkCancellationService {

    /** Cuántas reservas futuras confirmadas tiene el recurso (las que se cancelarían). */
    long countFutureOfResource(UUID resourceId);

    /** Cancela las reservas futuras confirmadas del recurso; devuelve cuántas. */
    int cancelFutureOfResource(UUID resourceId, CancelOrigin origin, String reason);

    /** Cancela las reservas futuras confirmadas que hizo un cliente (HU-28); devuelve cuántas. */
    int cancelFutureOfClient(UUID clientId, CancelOrigin origin, String reason);

    /** Cancela las reservas futuras confirmadas de unos negocios (HU-28, proveedor eliminado); devuelve cuántas. */
    int cancelFutureOfBusinesses(Collection<UUID> businessIds, CancelOrigin origin, String reason);
}
