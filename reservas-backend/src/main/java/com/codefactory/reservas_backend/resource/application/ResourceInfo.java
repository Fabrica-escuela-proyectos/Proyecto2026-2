package com.codefactory.reservas_backend.resource.application;

import java.util.UUID;

/** Datos de un recurso que el módulo Resource expone a otros módulos (ADR-003). */
public record ResourceInfo(UUID id, UUID businessId, String name, String type, boolean active) {
}
