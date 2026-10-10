package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.BookingQueryService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BusinessBookingPageResponse;
import com.codefactory.reservas_backend.common.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-24 - Consultar las reservas del negocio. Solo el PROVEEDOR dueño del negocio
 * (otro proveedor, cliente o administrador: 403; sin sesión: 401; negocio inexistente: 404).
 */
@RestController
@Tag(name = OpenApiTags.PROVIDER_BOOKINGS)
@RequestMapping("/api/v1/businesses/{businessId}/bookings")
@RequiredArgsConstructor
public class BusinessBookingsController {

    private final BookingQueryService bookingQueryService;
    private final IdentityService identityService;

    @Operation(summary = "Reservas del negocio (HU-24)",
            description = "Solo el proveedor dueño del negocio. Incluye los datos del cliente guardados en la reserva (`clientId`, `clientName`, `clientEmail`). Orden: inicio descendente.")
    @ApiResponse(responseCode = "200", description = "Página de reservas del negocio")
    @ApiResponse(responseCode = "400", description = "Fecha, estado o paginación inválidos")
    @ApiResponse(responseCode = "403", description = "El negocio es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El negocio no existe")
    @GetMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<BusinessBookingPageResponse> list(
            @PathVariable UUID businessId,
            @Parameter(description = "Desde esta fecha, inclusive (yyyy-MM-dd, día de Bogotá)", example = "2026-10-01") @RequestParam(required = false) String from,
            @Parameter(description = "Hasta esta fecha, inclusive (yyyy-MM-dd, día de Bogotá)", example = "2026-10-31") @RequestParam(required = false) String to,
            @Parameter(description = "Filtrar por estado: CONFIRMADA, CANCELADA o COMPLETADA (sin distinguir mayúsculas)", example = "CONFIRMADA") @RequestParam(required = false) String status,
            @Parameter(description = "Página, desde 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página: por defecto 20, máximo 50 (si se pide más, se recorta)") @RequestParam(defaultValue = "" + BookingQueryService.DEFAULT_SIZE) int size) {
        UserIdentity provider = identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
        return ResponseEntity.ok(bookingQueryService.listForBusiness(businessId, provider, from, to, status, page, size));
    }
}
