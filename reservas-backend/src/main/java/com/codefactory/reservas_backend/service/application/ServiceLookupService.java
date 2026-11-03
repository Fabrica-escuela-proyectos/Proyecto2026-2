package com.codefactory.reservas_backend.service.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta de servicios para otros módulos (HU-22 crea reservas sobre un
 * servicio; HU-23..28 las listan). Evita que Reservation importe las
 * entidades o repositorios de Service (ADR-003).
 */
public interface ServiceLookupService {

    Optional<ServiceInfo> findById(UUID serviceId);

    /** Ids de los recursos asignados al servicio (HU-18), activos o no. */
    List<UUID> assignedResourceIds(UUID serviceId);

    /**
     * Bloqueo compartido del servicio hasta el fin de la transacción (HU-22 con HU-10/HU-11): la reserva
     * espera a una edición o desactivación en curso y viceversa. Debe llamarse ANTES de {@link #findById}
     * para leer los datos ya confirmados. Devuelve si el servicio existe y está activo.
     */
    boolean lockForBooking(UUID serviceId);
}
