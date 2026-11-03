package com.codefactory.reservas_backend.resource.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Un cierre puntual de un recurso (HU-21). {@code start}/{@code end} van en hora local de Bogotá con el
 * formato {@code yyyy-MM-ddTHH:mm}; {@code fullDay} indica que cubre días completos.
 */
public record ClosureInfo(UUID id, UUID resourceId, String start, String end, boolean fullDay, String reason,
                          Instant createdAt) {
}
