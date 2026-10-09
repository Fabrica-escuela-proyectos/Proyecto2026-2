package com.codefactory.reservas_backend.resource.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.resource.application.ResourceService;
import com.codefactory.reservas_backend.resource.controller.dto.CreateResourceRequest;
import com.codefactory.reservas_backend.resource.controller.dto.ResourceResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * HU-14 - Registrar recurso. POST y GET (listado) en
 * /api/v1/businesses/{businessId}/resources, solo para el PROVEEDOR dueño del
 * negocio (docs/api/endpoints-sprint-2.md).
 */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;
    private final IdentityService identityService;

    @PostMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<ResourceResponse> create(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateResourceRequest request,
            HttpServletRequest httpRequest) {
        ResourceResponse response = resourceService.create(
                businessId, request, currentUser(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<List<ResourceResponse>> list(@PathVariable UUID businessId) {
        return ResponseEntity.ok(resourceService.list(businessId, currentUser()));
    }

    private UserIdentity currentUser() {
        return identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
    }
}
