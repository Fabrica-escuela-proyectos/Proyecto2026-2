package com.codefactory.reservas_backend.provider.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.ProviderQueryService;
import com.codefactory.reservas_backend.provider.application.ProviderRegistrationService;
import com.codefactory.reservas_backend.provider.controller.dto.ProviderResponse;
import com.codefactory.reservas_backend.provider.controller.dto.RegisterProviderRequest;
import com.codefactory.reservas_backend.provider.controller.dto.RegisterProviderResponse;
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
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderRegistrationService providerRegistrationService;
    private final ProviderQueryService providerQueryService;
    private final IdentityService identityService;

    @PostMapping
    public ResponseEntity<RegisterProviderResponse> register(
            @Valid @RequestBody RegisterProviderRequest request,
            HttpServletRequest httpRequest) {

        RegisterProviderResponse response = providerRegistrationService.register(request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<ProviderResponse> getOwn() {
        return ResponseEntity.ok(providerQueryService.getOwnProvider(currentUser().id()));
    }

    @GetMapping("/{providerId}")
    public ResponseEntity<ProviderResponse> getProvider(@PathVariable UUID providerId) {
        return ResponseEntity.ok(providerQueryService.getProvider(providerId, currentUser()));
    }

    private UserIdentity currentUser() {
        return identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
    }
}
