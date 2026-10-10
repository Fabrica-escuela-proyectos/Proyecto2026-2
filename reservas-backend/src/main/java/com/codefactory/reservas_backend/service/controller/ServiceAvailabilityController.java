package com.codefactory.reservas_backend.service.controller;

import com.codefactory.reservas_backend.service.application.ServiceAvailabilityService;
import com.codefactory.reservas_backend.service.controller.dto.ServiceAvailabilityDtos.ServiceAvailabilityResponse;
import com.codefactory.reservas_backend.common.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
 * HU-20 - Consultar disponibilidad de un servicio. Solo lectura y PÚBLICA (sin
 * sesión), como pide el escenario "usuario sin sesión iniciada" de la HU: la
 * excepción está en SecurityConfig y solo cubre este GET.
 */
@RestController
@Tag(name = OpenApiTags.CATALOG)
@RequestMapping("/api/v1/services/{serviceId}/availability")
@RequiredArgsConstructor
public class ServiceAvailabilityController {

    private final ServiceAvailabilityService availabilityService;

    @Operation(summary = "Horarios libres de un servicio (HU-20)",
            description = "PÚBLICA: no requiere sesión. Cruza los recursos activos asignados al servicio, sus horarios, la antelación mínima del negocio y las reservas confirmadas. Los horarios avanzan de a la duración del servicio. Sin `date` se consulta hoy (hora de Bogotá); máximo 365 días hacia adelante.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Horarios libres, cada uno con los recursos que pueden atenderlo")
    @ApiResponse(responseCode = "400", description = "Fecha con formato inválido, pasada o a más de 365 días")
    @ApiResponse(responseCode = "404", description = "El servicio no está disponible (no existe, está inactivo o su proveedor está inactivo)")
    @GetMapping
    public ResponseEntity<ServiceAvailabilityResponse> get(
            @PathVariable UUID serviceId,
            @Parameter(description = "Fecha yyyy-MM-dd; sin ella, hoy en hora de Bogotá", example = "2026-10-19") @RequestParam(required = false) String date) {
        return ResponseEntity.ok(availabilityService.getAvailability(serviceId, date));
    }
}
