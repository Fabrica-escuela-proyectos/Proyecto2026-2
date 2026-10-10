package com.codefactory.reservas_backend.resource.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.resource.application.ResourceInfo;
import com.codefactory.reservas_backend.resource.application.ResourceLifecycleService;
import com.codefactory.reservas_backend.resource.controller.dto.ResourceStatusResponse;
import com.codefactory.reservas_backend.common.config.OpenApiTags;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-17 - Reactivar recurso: POST /api/v1/resources/{resourceId}/reactivation. Solo el PROVEEDOR
 * dueño del negocio del recurso; reactiva el mismo recurso (sin registrarlo de nuevo) y es idempotente.
 */
@RestController
@Tag(name = OpenApiTags.RESOURCES)
@RequiredArgsConstructor
public class ResourceReactivationController {

    private final ResourceLifecycleService lifecycleService;
    private final IdentityService identityService;

    @Operation(summary = "Reactivar un recurso (HU-17)",
            description = "Solo el proveedor dueño. Reactiva el mismo recurso (no crea otro) y es idempotente. Las reservas canceladas al desactivarlo no se restauran.")
    @ApiResponse(responseCode = "200", description = "Recurso activo")
    @ApiResponse(responseCode = "403", description = "El recurso es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El recurso no existe")
    @PostMapping("/api/v1/resources/{resourceId}/reactivation")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<ResourceStatusResponse> reactivate(@PathVariable UUID resourceId, HttpServletRequest httpRequest) {
        UserIdentity requester = identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
        ResourceInfo info = lifecycleService.reactivate(resourceId, requester, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(new ResourceStatusResponse(info.id(), info.name(), info.active()));
    }
}
