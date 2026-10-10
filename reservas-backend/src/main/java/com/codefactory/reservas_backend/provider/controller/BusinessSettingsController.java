package com.codefactory.reservas_backend.provider.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessSettingsService;
import com.codefactory.reservas_backend.provider.controller.dto.BookingLeadTimeRequest;
import com.codefactory.reservas_backend.provider.controller.dto.BookingLeadTimeResponse;
import com.codefactory.reservas_backend.common.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-08 - Definir antelación mínima de reserva. Solo el PROVEEDOR dueño del
 * negocio (docs/api/endpoints-sprint-2.md). El GET de la antelación vive en su
 * propia ruta para dejar libre GET /api/v1/businesses/{businessId}, que es el
 * detalle del catálogo de HU-13.
 */
@RestController
@Tag(name = OpenApiTags.PROVIDERS)
@RequestMapping("/api/v1/businesses/{businessId}/booking-lead-time")
@RequiredArgsConstructor
public class BusinessSettingsController {

    private final BusinessSettingsService businessSettingsService;
    private final IdentityService identityService;

    @Operation(summary = "Consultar la antelación mínima de reserva (HU-08)",
            description = "Solo el proveedor dueño del negocio. Horas enteras entre 1 y 720; por defecto 1.")
    @ApiResponse(responseCode = "200", description = "Antelación mínima en horas")
    @ApiResponse(responseCode = "403", description = "El negocio es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El negocio no existe")
    @GetMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<BookingLeadTimeResponse> get(@PathVariable UUID businessId) {
        return ResponseEntity.ok(businessSettingsService.getBookingLeadTime(businessId, currentUser()));
    }

    @Operation(summary = "Definir la antelación mínima de reserva (HU-08)",
            description = "Solo el proveedor dueño del negocio. Se aplica a las reservas nuevas y a la disponibilidad (HU-20).")
    @ApiResponse(responseCode = "200", description = "Antelación actualizada")
    @ApiResponse(responseCode = "400", description = "Las horas deben ser un entero entre 1 y 720")
    @ApiResponse(responseCode = "403", description = "El negocio es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El negocio no existe")
    @PutMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<BookingLeadTimeResponse> update(
            @PathVariable UUID businessId,
            @Valid @RequestBody BookingLeadTimeRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(businessSettingsService.updateBookingLeadTime(
                businessId, request.getHours(), currentUser(), httpRequest.getRemoteAddr()));
    }

    private UserIdentity currentUser() {
        return identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
    }
}
