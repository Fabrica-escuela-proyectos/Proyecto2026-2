package com.codefactory.reservas_backend.provider.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.ProviderQueryService;
import com.codefactory.reservas_backend.provider.application.ProviderRegistrationService;
import com.codefactory.reservas_backend.provider.controller.dto.ProviderResponse;
import com.codefactory.reservas_backend.provider.controller.dto.RegisterProviderRequest;
import com.codefactory.reservas_backend.provider.controller.dto.RegisterProviderResponse;
import com.codefactory.reservas_backend.common.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-03 - Registro de proveedor de servicios.
 * Contrato: POST /api/v1/providers (endpoints-sprint-1.md sección 4).
 *
 * GET /me y GET /{providerId} no están en endpoints-sprint-1.md (ese
 * documento no llega a definir endpoints propios de Provider más allá del
 * registro), pero son necesarios para tener un recurso real sobre el cual
 * demostrar la regla de pertenencia de HU-06 — ver Javadoc de
 * ProviderQueryService.
 */
@RestController
@Tag(name = OpenApiTags.PROVIDERS)
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderRegistrationService providerRegistrationService;
    private final ProviderQueryService providerQueryService;
    private final IdentityService identityService;

    @Operation(summary = "Registrar un proveedor y su negocio (HU-03)",
            description = "Crea la cuenta con rol PROVEEDOR y su negocio. Comparte con el registro de clientes el límite de 5 registros por IP cada 10 minutos.")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Proveedor y negocio creados")
    @ApiResponse(responseCode = "400", description = "Campos obligatorios ausentes o con formato inválido")
    @ApiResponse(responseCode = "409", description = "El correo o el celular ya están registrados")
    @ApiResponse(responseCode = "429", description = "Límite de registros por IP alcanzado")
    @PostMapping
    public ResponseEntity<RegisterProviderResponse> register(
            @Valid @RequestBody RegisterProviderRequest request,
            HttpServletRequest httpRequest) {

        RegisterProviderResponse response = providerRegistrationService.register(request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Consultar mi perfil de proveedor (HU-06)",
            description = "El perfil y los negocios del usuario autenticado.")
    @ApiResponse(responseCode = "200", description = "Perfil del proveedor")
    @ApiResponse(responseCode = "404", description = "El usuario no tiene un negocio asociado")
    @GetMapping("/me")
    public ResponseEntity<ProviderResponse> getOwn() {
        return ResponseEntity.ok(providerQueryService.getOwnProvider(currentUser().id()));
    }

    @Operation(summary = "Consultar un proveedor por id (HU-06)",
            description = "Solo el propio proveedor o un administrador.")
    @ApiResponse(responseCode = "200", description = "Perfil del proveedor")
    @ApiResponse(responseCode = "403", description = "Es el perfil de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El proveedor no existe")
    @GetMapping("/{providerId}")
    public ResponseEntity<ProviderResponse> getProvider(@PathVariable UUID providerId) {
        return ResponseEntity.ok(providerQueryService.getProvider(providerId, currentUser()));
    }

    private UserIdentity currentUser() {
        return identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
    }
}
