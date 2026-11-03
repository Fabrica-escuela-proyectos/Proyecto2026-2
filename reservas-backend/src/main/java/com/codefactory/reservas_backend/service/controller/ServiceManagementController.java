package com.codefactory.reservas_backend.service.controller;

import com.codefactory.reservas_backend.common.config.OpenApiTags;
import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.service.application.ServiceInfo;
import com.codefactory.reservas_backend.service.application.ServiceLifecycleService;
import com.codefactory.reservas_backend.service.application.ServiceOfferingService;
import com.codefactory.reservas_backend.service.controller.dto.ServiceResponse;
import com.codefactory.reservas_backend.service.controller.dto.UpdateServiceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-10 (editar) y HU-12 (reactivar) un servicio, por su id. La desactivación (HU-11) vive en el módulo de
 * reservas porque puede cancelar reservas futuras (ServiceDeactivationController). Solo el PROVEEDOR dueño.
 */
@RestController
@Tag(name = OpenApiTags.SERVICES)
@RequiredArgsConstructor
public class ServiceManagementController {

    /** Estado de un servicio tras reactivarlo. */
    public record ServiceStatusResponse(UUID id, String name, boolean active) {
    }

    private final ServiceOfferingService serviceOfferingService;
    private final ServiceLifecycleService lifecycleService;
    private final IdentityService identityService;

    @Operation(summary = "Editar un servicio (HU-10)",
            description = "Solo el proveedor dueño. Edición parcial: se envía solo lo que cambia (`name`, `description`, `durationMinutes`, `priceCop`). Mismos límites que al crear; el precio puede ser 0. **Las reservas ya hechas conservan su precio y su horario.** Una descripción vacía la borra.")
    @ApiResponse(responseCode = "200", description = "Servicio actualizado")
    @ApiResponse(responseCode = "400", description = "Nombre vacío, duración o precio fuera de rango")
    @ApiResponse(responseCode = "403", description = "El servicio es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El servicio no existe")
    @ApiResponse(responseCode = "409", description = "Ya existe un servicio con ese nombre en el negocio")
    @PatchMapping("/api/v1/services/{serviceId}")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<ServiceResponse> update(@PathVariable UUID serviceId,
                                                  @Valid @RequestBody UpdateServiceRequest request,
                                                  HttpServletRequest httpRequest) {
        return ResponseEntity.ok(serviceOfferingService.update(serviceId, request, currentUser(), httpRequest.getRemoteAddr()));
    }

    @Operation(summary = "Reactivar un servicio (HU-12)",
            description = "Solo el proveedor dueño. Reactiva el mismo servicio con sus datos (no crea otro) y es idempotente. Las reservas canceladas al desactivarlo siguen canceladas.")
    @ApiResponse(responseCode = "200", description = "Servicio activo")
    @ApiResponse(responseCode = "403", description = "El servicio es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El servicio no existe")
    @PostMapping("/api/v1/services/{serviceId}/reactivation")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<ServiceStatusResponse> reactivate(@PathVariable UUID serviceId, HttpServletRequest httpRequest) {
        ServiceInfo info = lifecycleService.reactivate(serviceId, currentUser(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(new ServiceStatusResponse(info.id(), info.name(), info.active()));
    }

    private UserIdentity currentUser() {
        return identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
    }
}
