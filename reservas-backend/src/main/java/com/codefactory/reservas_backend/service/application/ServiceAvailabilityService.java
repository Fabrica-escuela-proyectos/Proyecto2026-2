package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.service.controller.dto.ServiceAvailabilityDtos.ServiceAvailabilityResponse;

import java.util.UUID;

/**
 * HU-20 - Horarios libres de un servicio en una fecha. Cruza los recursos
 * activos asignados (HU-18), sus horarios (HU-19), la antelación mínima del
 * negocio (HU-08) y los tiempos ya ocupados ({@link BusyTimeSource}, HU-22).
 */
public interface ServiceAvailabilityService {

    /** Hasta cuántos días hacia adelante se puede consultar (supuesto del equipo). */
    int MAX_DAYS_AHEAD = 365;

    /**
     * @param date fecha {@code yyyy-MM-dd}; {@code null} o vacía = hoy (hora de Bogotá)
     * @throws com.codefactory.reservas_backend.service.domain.InvalidAvailabilityQueryException fecha inválida, pasada o muy lejana (400)
     * @throws com.codefactory.reservas_backend.service.domain.ServiceNotAvailableException servicio inexistente, inactivo o de un proveedor inactivo (404)
     */
    ServiceAvailabilityResponse getAvailability(UUID serviceId, String date);
}
