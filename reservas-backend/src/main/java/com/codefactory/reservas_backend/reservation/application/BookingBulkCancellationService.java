package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;

import java.time.Instant;
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

    /** HU-11: cuántas reservas futuras confirmadas tiene el servicio. */
    long countFutureOfService(UUID serviceId);

    /** HU-11: cancela las reservas futuras confirmadas del servicio; devuelve cuántas. */
    int cancelFutureOfService(UUID serviceId, CancelOrigin origin, String reason);

    /** HU-21: cuántas reservas confirmadas del recurso, aún no iniciadas, se traslapan con [start, end). */
    long countFutureOfResourceIn(UUID resourceId, Instant start, Instant end);

    /** HU-21: cancela las reservas confirmadas del recurso, aún no iniciadas, que se traslapan con [start, end). */
    int cancelFutureOfResourceIn(UUID resourceId, Instant start, Instant end, CancelOrigin origin, String reason);

    /** Nombre que muestran las reservas de un cliente cuya cuenta se eliminó (P12, Calidad 2026-11-06). */
    String DELETED_CLIENT_NAME = "Cliente eliminado";

    /**
     * P12: borra el nombre y el correo del cliente de TODAS sus reservas (pasadas y futuras) y deja
     * {@link #DELETED_CLIENT_NAME}. El resto del historial (servicio, recurso, horario, precio y estado) se conserva.
     */
    int anonymizeClient(UUID clientId);
}
