package com.codefactory.reservas_backend.service.controller.dto;

import java.util.List;
import java.util.UUID;

/** Respuesta de la consulta de horarios libres de un servicio (HU-20). */
public final class ServiceAvailabilityDtos {

    private ServiceAvailabilityDtos() {
    }

    /** Recurso en el que se puede atender un horario. */
    public record SlotResource(UUID id, String name) {
    }

    /** Horario libre [start, end) con los recursos que lo pueden atender (HU-22 recibe {@code startAt} y, opcional, un recurso). */
    public record Slot(String start, String end, List<SlotResource> resources) {
    }

    /** {@code message} solo viene cuando no hay horarios libres ese día. */
    public record ServiceAvailabilityResponse(UUID serviceId, String serviceName, String date, String timezone,
                                              int durationMinutes, List<Slot> slots, String message) {
    }
}
