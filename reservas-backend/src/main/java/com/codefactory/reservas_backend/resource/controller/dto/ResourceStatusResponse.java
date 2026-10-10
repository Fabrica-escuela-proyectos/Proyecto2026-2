package com.codefactory.reservas_backend.resource.controller.dto;

import java.util.UUID;

/** Estado de un recurso tras reactivarlo (HU-17). */
public record ResourceStatusResponse(UUID resourceId, String name, boolean active) {
}
