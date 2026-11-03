package com.codefactory.reservas_backend.provider.controller;

import com.codefactory.reservas_backend.common.config.OpenApiTags;
import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessProfile;
import com.codefactory.reservas_backend.provider.application.BusinessProfileService;
import com.codefactory.reservas_backend.provider.controller.dto.UpdateBusinessRequest;
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

/**
 * HU-07 - Editar información del negocio: PATCH /api/v1/businesses/{businessId}. El GET de la misma ruta es
 * el detalle del catálogo (HU-13, CatalogController), que ya muestra los datos editados.
 */
@RestController
@Tag(name = OpenApiTags.PROVIDERS)
@RequiredArgsConstructor
public class BusinessProfileController {

    /** Respuesta: el negocio actualizado y el aviso que pide el AC. */
    public record UpdateBusinessResponse(BusinessProfile business, String message) {
    }

    private final BusinessProfileService profileService;
    private final IdentityService identityService;

    @Operation(summary = "Editar la información del negocio (HU-07)",
            description = "Solo el proveedor dueño. Edición parcial: se envía solo lo que cambia. Campos: `name` (obligatorio, no puede quedar vacío), `description`, `address`, `businessType` (SALUD, BELLEZA, BIENESTAR, DEPORTE, EDUCACION, CONSULTORIA, ENTRETENIMIENTO, OTRO) y `phone` (10 dígitos, celular 3… o fijo 60…). En los opcionales, una cadena vacía borra el dato. Si algo es inválido no se guarda nada. Los demás negocios del proveedor no cambian y los clientes ven los datos nuevos en `GET /api/v1/businesses/{businessId}`.")
    @ApiResponse(responseCode = "200", description = "Información actualizada")
    @ApiResponse(responseCode = "400", description = "Nombre vacío, teléfono o tipo de negocio inválidos, o textos demasiado largos")
    @ApiResponse(responseCode = "403", description = "El negocio es de otro proveedor, o el rol no es PROVEEDOR")
    @ApiResponse(responseCode = "404", description = "El negocio no existe")
    @PatchMapping("/api/v1/businesses/{businessId}")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<UpdateBusinessResponse> update(@PathVariable UUID businessId,
                                                         @Valid @RequestBody UpdateBusinessRequest request,
                                                         HttpServletRequest httpRequest) {
        UserIdentity requester = identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
        BusinessProfile profile = profileService.update(businessId, request, requester, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(new UpdateBusinessResponse(profile, "La información del negocio fue actualizada"));
    }
}
