package com.codefactory.reservas_backend.resource.controller;

import com.codefactory.reservas_backend.common.config.OpenApiTags;
import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.resource.application.ResourceService;
import com.codefactory.reservas_backend.resource.controller.dto.ResourceResponse;
import com.codefactory.reservas_backend.resource.controller.dto.UpdateResourceRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** HU-15 - Editar recurso: PATCH /api/v1/resources/{resourceId}. Solo el PROVEEDOR dueño del negocio del recurso. */
@RestController
@Tag(name = OpenApiTags.RESOURCES)
@RequiredArgsConstructor
public class ResourceEditController {

    private final ResourceService resourceService;
    private final IdentityService identityService;

    @Operation(summary = "Editar un recurso (HU-15)",
            description = "Solo el proveedor dueño. Edición parcial: se envía solo lo que cambia (`name` y/o `type`). El nombre no puede quedar vacío y sigue siendo único en el negocio. El negocio del recurso no cambia (un `businessId` en el cuerpo se ignora). Las reservas ya hechas conservan el nombre que tenían.")
    @ApiResponse(responseCode = "200", description = "Recurso actualizado")
    @ApiResponse(responseCode = "400", description = "Nombre vacío o demasiado largo, o tipo inválido")
    @ApiResponse(responseCode = "403", description = "El recurso es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El recurso no existe")
    @ApiResponse(responseCode = "409", description = "Ya existe un recurso con ese nombre en el negocio")
    @PatchMapping("/api/v1/resources/{resourceId}")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<ResourceResponse> update(@PathVariable UUID resourceId,
                                                   @Valid @RequestBody UpdateResourceRequest request,
                                                   HttpServletRequest httpRequest) {
        UserIdentity requester = identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
        return ResponseEntity.ok(resourceService.update(resourceId, request, requester, httpRequest.getRemoteAddr()));
    }
}
