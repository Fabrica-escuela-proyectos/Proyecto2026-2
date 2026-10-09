package com.codefactory.reservas_backend.service.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Punto de extensión: tiempos en que un recurso ya está ocupado (reservas
 * CONFIRMADAS). El motor de disponibilidad (HU-20) descuenta la unión de todos
 * los beans que implementen esta interfaz. Hoy no hay ninguno; el módulo de
 * reservas (HU-22) registrará el suyo, sin tocar el motor.
 */
public interface BusyTimeSource {

    /** Intervalos [start, end) ocupados de esos recursos en esa fecha, en hora local del negocio. */
    List<BusyInterval> busyOn(Collection<UUID> resourceIds, LocalDate date);

    record BusyInterval(UUID resourceId, LocalDateTime start, LocalDateTime end) {
    }
}
