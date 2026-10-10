package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.ResourceDeactivationService;
import com.codefactory.reservas_backend.reservation.application.ResourceDeactivationService.DeactivationResult;
import com.codefactory.reservas_backend.common.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-16 - Desactivar recurso: POST /api/v1/resources/{resourceId}/deactivation, cuerpo opcional
 * {@code {"confirm": true}}. Solo el PROVEEDOR dueño del negocio del recurso. Si el recurso tiene reservas
 * futuras y no se confirma, responde 409 CONFIRMATION_REQUIRED con la cantidad y no cambia nada.
 */
@RestController
@Tag(name = OpenApiTags.RESOURCES)
@RequiredArgsConstructor
public class ResourceDeactivationController {

    private final ResourceDeactivationService deactivationService;
    private final IdentityService identityService;

    @Getter
    @Setter
    public static class DeactivationRequest {
        private boolean confirm;
    }

    @Operation(summary = "Desactivar un recurso (HU-16)",
            description = "Solo el proveedor dueño. Si el recurso tiene reservas futuras confirmadas y no se envía `confirm: true`, responde 409 CONFIRMATION_REQUIRED con la cantidad en `fields.affectedBookings` y NO cambia nada. Con confirmación, cancela esas reservas (origen RECURSO_NO_DISPONIBLE) y desactiva el recurso en una sola transacción. El cuerpo es opcional.")
    @ApiResponse(responseCode = "200", description = "Recurso desactivado y reservas canceladas (con su cantidad)")
    @ApiResponse(responseCode = "403", description = "El recurso es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El recurso no existe")
    @ApiResponse(responseCode = "409", description = "CONFIRMATION_REQUIRED: hay reservas futuras y falta `confirm: true`")
    @PostMapping("/api/v1/resources/{resourceId}/deactivation")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<DeactivationResult> deactivate(@PathVariable UUID resourceId,
                                                         @RequestBody(required = false) DeactivationRequest request,
                                                         HttpServletRequest httpRequest) {
        UserIdentity requester = identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
        boolean confirm = request != null && request.isConfirm();
        return ResponseEntity.ok(deactivationService.deactivate(resourceId, confirm, requester, httpRequest.getRemoteAddr()));
    }
}
