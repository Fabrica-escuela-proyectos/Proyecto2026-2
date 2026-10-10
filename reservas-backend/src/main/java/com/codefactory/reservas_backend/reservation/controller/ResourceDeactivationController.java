package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.ResourceDeactivationService;
import com.codefactory.reservas_backend.reservation.application.ResourceDeactivationService.DeactivationResult;
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
@RequiredArgsConstructor
public class ResourceDeactivationController {

    private final ResourceDeactivationService deactivationService;
    private final IdentityService identityService;

    @Getter
    @Setter
    public static class DeactivationRequest {
        private boolean confirm;
    }

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
