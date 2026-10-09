package com.codefactory.reservas_backend.service.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.service.application.ServiceResourceAssignmentService;
import com.codefactory.reservas_backend.service.controller.dto.ServiceResourcesDtos.AssignResourcesRequest;
import com.codefactory.reservas_backend.service.controller.dto.ServiceResourcesDtos.ServiceResourcesResponse;
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
 * HU-18 - Asignar recursos a un servicio. PUT (reemplaza el conjunto) y GET en
 * /api/v1/services/{serviceId}/resources, solo para el PROVEEDOR dueño del
 * negocio del servicio (docs/api/endpoints-sprint-2.md).
 */
@RestController
@RequestMapping("/api/v1/services/{serviceId}/resources")
@RequiredArgsConstructor
public class ServiceResourceController {

    private final ServiceResourceAssignmentService assignmentService;
    private final IdentityService identityService;

    @PutMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<ServiceResourcesResponse> replace(
            @PathVariable UUID serviceId,
            @Valid @RequestBody AssignResourcesRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(assignmentService.replaceResources(
                serviceId, request.getResourceIds(), currentUser(), httpRequest.getRemoteAddr()));
    }

    @GetMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<ServiceResourcesResponse> list(@PathVariable UUID serviceId) {
        return ResponseEntity.ok(assignmentService.getResources(serviceId, currentUser()));
    }

    private UserIdentity currentUser() {
        return identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
    }
}
