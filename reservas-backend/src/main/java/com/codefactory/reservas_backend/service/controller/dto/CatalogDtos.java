package com.codefactory.reservas_backend.service.controller.dto;

import java.util.List;
import java.util.UUID;

/** Respuestas del catálogo de negocios y servicios (HU-13). Solo lectura. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    /** Negocio en el listado: se identifica por su nombre. */
    public record BusinessItem(UUID id, String name) {
    }

    /** Servicio visible para un cliente: nombre, descripción, duración y precio. */
    public record ServiceItem(UUID id, String name, String description, int durationMinutes, long priceCop) {
    }

    /** Página del catálogo; {@code message} solo viene cuando no hay resultados. */
    public record BusinessPageResponse(List<BusinessItem> items, int page, int size, long totalElements,
                                       int totalPages, String message) {
    }

    /** Detalle de un negocio con sus servicios ACTIVOS; {@code message} solo si no tiene ninguno. */
    public record BusinessDetailResponse(UUID id, String name, List<ServiceItem> services, String message) {
    }
}
