package com.codefactory.reservas_backend.service.application;

import java.util.UUID;

/** Datos de un servicio que el módulo Service expone a otros módulos (ADR-003). */
public record ServiceInfo(UUID id, UUID businessId, String name, int durationMinutes, long priceCop, boolean active) {
}
