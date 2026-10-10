package com.codefactory.reservas_backend.resource.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.resource.application.ResourceService;
import com.codefactory.reservas_backend.resource.controller.dto.CreateResourceRequest;
import com.codefactory.reservas_backend.resource.controller.dto.ResourceResponse;
import com.codefactory.reservas_backend.common.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = OpenApiTags.RESOURCES)
@RequestMapping("/api/v1/businesses/{businessId}/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;
    private final IdentityService identityService;

    @Operation(summary = "Registrar un recurso (HU-14)",
            description = "Solo el proveedor dueño del negocio. Tipos: SALA, EQUIPO o PERSONAL. El nombre es único por negocio. Un `businessId` en el cuerpo se ignora: manda el de la ruta.")
    @ApiResponse(responseCode = "201", description = "Recurso creado")
    @ApiResponse(responseCode = "400", description = "Nombre o tipo inválidos")
    @ApiResponse(responseCode = "403", description = "El negocio es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El negocio no existe")
    @ApiResponse(responseCode = "409", description = "Ya existe un recurso con ese nombre en el negocio")
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

    @Operation(summary = "Listar los recursos del negocio (HU-14)",
            description = "Solo el proveedor dueño del negocio. Incluye también los inactivos.")
    @ApiResponse(responseCode = "200", description = "Recursos del negocio")
    @ApiResponse(responseCode = "403", description = "El negocio es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El negocio no existe")
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
