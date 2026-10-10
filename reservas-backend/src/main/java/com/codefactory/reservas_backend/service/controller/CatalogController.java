package com.codefactory.reservas_backend.service.controller;

import com.codefactory.reservas_backend.service.application.CatalogService;
import com.codefactory.reservas_backend.service.controller.dto.CatalogDtos.BusinessDetailResponse;
import com.codefactory.reservas_backend.service.controller.dto.CatalogDtos.BusinessPageResponse;
import com.codefactory.reservas_backend.common.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-13 - Consultar negocios y servicios. Solo lectura, para cualquier usuario
 * autenticado (sin sesión: 401; la regla general de SecurityConfig). A diferencia
 * de este catálogo, HU-20 (disponibilidad) es pública: ver ServiceAvailabilityController.
 */
@RestController
@Tag(name = OpenApiTags.CATALOG)
@RequestMapping("/api/v1/businesses")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @Operation(summary = "Listar negocios (HU-13)",
            description = "Cualquier usuario con sesión. Ordenado por nombre. Una página fuera de rango devuelve la lista vacía con un mensaje.")
    @ApiResponse(responseCode = "200", description = "Página de negocios")
    @ApiResponse(responseCode = "400", description = "page negativo o size menor que 1")
    @GetMapping
    public ResponseEntity<BusinessPageResponse> list(
            @Parameter(description = "Página, desde 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página: por defecto 20, máximo 50 (si se pide más, se recorta)") @RequestParam(defaultValue = "" + CatalogService.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(catalogService.listBusinesses(page, size));
    }

    @Operation(summary = "Detalle de un negocio con sus servicios activos (HU-13)",
            description = "Solo se listan los servicios activos; si no hay ninguno, la respuesta lo indica en `message`.")
    @ApiResponse(responseCode = "200", description = "Negocio y sus servicios activos")
    @ApiResponse(responseCode = "404", description = "El negocio no existe")
    @GetMapping("/{businessId}")
    public ResponseEntity<BusinessDetailResponse> detail(@PathVariable UUID businessId) {
        return ResponseEntity.ok(catalogService.getBusiness(businessId));
    }
}
