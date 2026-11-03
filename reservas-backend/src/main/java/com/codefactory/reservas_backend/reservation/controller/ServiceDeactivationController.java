package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.common.config.OpenApiTags;
import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.ServiceDeactivationService;
import com.codefactory.reservas_backend.reservation.application.ServiceDeactivationService.DeactivationResult;
import com.codefactory.reservas_backend.reservation.controller.ResourceDeactivationController.DeactivationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-11 - Desactivar servicio: POST /api/v1/services/{serviceId}/deactivation, cuerpo opcional
 * {@code {"confirm": true}}. Mismo contrato que la desactivación de un recurso (HU-16).
 */
@RestController
@Tag(name = OpenApiTags.SERVICES)
@RequiredArgsConstructor
public class ServiceDeactivationController {

    private final ServiceDeactivationService deactivationService;
    private final IdentityService identityService;

    @Operation(summary = "Desactivar un servicio (HU-11)",
            description = "Solo el proveedor dueño. Si el servicio tiene reservas futuras confirmadas y no se envía `confirm: true`, responde 409 CONFIRMATION_REQUIRED con la cantidad en `fields.affectedBookings` y NO cambia nada. Con confirmación, cancela esas reservas (origen SERVICIO_NO_DISPONIBLE, sin la regla de 1 hora) y desactiva el servicio en una sola transacción. Un servicio inactivo no se puede reservar ni aparece en el catálogo.")
    @ApiResponse(responseCode = "200", description = "Servicio desactivado y reservas canceladas (con su cantidad)")
    @ApiResponse(responseCode = "403", description = "El servicio es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El servicio no existe")
    @ApiResponse(responseCode = "409", description = "CONFIRMATION_REQUIRED: hay reservas futuras y falta `confirm: true`")
    @PostMapping("/api/v1/services/{serviceId}/deactivation")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<DeactivationResult> deactivate(@PathVariable UUID serviceId,
                                                         @RequestBody(required = false) DeactivationRequest request,
                                                         HttpServletRequest httpRequest) {
        UserIdentity requester = identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
        boolean confirm = request != null && request.isConfirm();
        return ResponseEntity.ok(deactivationService.deactivate(serviceId, confirm, requester, httpRequest.getRemoteAddr()));
    }
}
