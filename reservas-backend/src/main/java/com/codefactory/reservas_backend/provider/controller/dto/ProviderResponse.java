package com.codefactory.reservas_backend.provider.controller.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * Respuesta de GET /api/v1/providers/me y GET /api/v1/providers/{id}
 * (HU-06: "Proveedor gestiona únicamente los recursos de su propio
 * negocio").
 */
@Getter
@Builder
public class ProviderResponse {
    private UUID providerId;
    private UUID userId;
    private List<BusinessSummary> businesses;

    public record BusinessSummary(UUID id, String name) {
    }
}
