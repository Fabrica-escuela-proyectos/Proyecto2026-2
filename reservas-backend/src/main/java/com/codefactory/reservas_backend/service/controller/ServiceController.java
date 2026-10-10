package com.codefactory.reservas_backend.service.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.service.application.ServiceOfferingService;
import com.codefactory.reservas_backend.service.controller.dto.CreateServiceRequest;
import com.codefactory.reservas_backend.service.controller.dto.ServiceResponse;
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
 * HU-09 - Crear servicio. Contratos:
 * POST /api/v1/businesses/{businessId}/services y GET (listado), solo para el
 * PROVEEDOR dueño del negocio (docs/api/endpoints-sprint-2.md).
 */
@RestController
@Tag(name = OpenApiTags.SERVICES)
@RequestMapping("/api/v1/businesses/{businessId}/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceOfferingService serviceOfferingService;
    private final IdentityService identityService;

    @Operation(summary = "Crear un servicio (HU-09)",
            description = "Solo el proveedor dueño del negocio. Duración de 1 a 1440 minutos, precio entero en COP (0 = gratuito) y nombre único por negocio sin distinguir mayúsculas.")
    @ApiResponse(responseCode = "201", description = "Servicio creado")
    @ApiResponse(responseCode = "400", description = "Nombre, duración o precio inválidos")
    @ApiResponse(responseCode = "403", description = "El negocio es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El negocio no existe")
    @ApiResponse(responseCode = "409", description = "Ya existe un servicio con ese nombre en el negocio")
    @PostMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<ServiceResponse> create(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateServiceRequest request,
            HttpServletRequest httpRequest) {
        ServiceResponse response = serviceOfferingService.create(
                businessId, request, currentUser(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Listar los servicios del negocio (HU-09)",
            description = "Solo el proveedor dueño del negocio. Incluye también los inactivos.")
    @ApiResponse(responseCode = "200", description = "Servicios del negocio")
    @ApiResponse(responseCode = "403", description = "El negocio es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El negocio no existe")
    @GetMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<List<ServiceResponse>> list(@PathVariable UUID businessId) {
        return ResponseEntity.ok(serviceOfferingService.list(businessId, currentUser()));
    }

    private UserIdentity currentUser() {
        return identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
    }
}
