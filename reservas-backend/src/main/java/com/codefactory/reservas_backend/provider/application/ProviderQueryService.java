package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.provider.controller.dto.ProviderResponse;
import com.codefactory.reservas_backend.provider.domain.Business;
import com.codefactory.reservas_backend.provider.domain.Provider;
import com.codefactory.reservas_backend.provider.domain.ProviderNotFoundException;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import com.codefactory.reservas_backend.provider.infrastructure.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Demuestra HU-06 ("Proveedor gestiona únicamente los recursos de su propio
 * negocio" / "Proveedor intenta gestionar recursos de otro negocio -> el
 * sistema debe denegar el acceso... no debe considerarse suficiente que el
 * proveedor tenga permiso general para esa acción") sobre un recurso que sí
 * existe en Sprint 1 (Provider/Business, creados por HU-03). Los módulos
 * Service/Resource/Reservation todavía no tienen código (arquitectura-sprint-1.md
 * sección 6.11: "preparación arquitectónica" para sprints siguientes), así
 * que no se simulan endpoints de esos dominios solo para ejercitar la regla.
 */
@Service
@RequiredArgsConstructor
public class ProviderQueryService {

    private final ProviderRepository providerRepository;
    private final BusinessRepository businessRepository;

    public ProviderResponse getOwnProvider(UUID userId) {
        Provider provider = providerRepository.findByUserId(userId)
                .orElseThrow(() -> new ProviderNotFoundException("No existe un negocio asociado a este usuario"));
        return toResponse(provider);
    }

    public ProviderResponse getProvider(UUID providerId, UserIdentity requester) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException("El proveedor solicitado no existe"));

        boolean isAdmin = RoleName.ADMINISTRADOR.name().equals(requester.role());
        boolean isOwner = provider.getUserId().equals(requester.id());
        if (!isAdmin && !isOwner) {
            // "No debe considerarse suficiente que el proveedor tenga
            // permiso general para esa acción": el rol PROVEEDOR ya pasó
            // authorizeHttpRequests/@PreAuthorize (si aplicara); esta es la
            // verificación de pertenencia adicional que exige el escenario.
            throw new AccessDeniedException("No tiene acceso a la información de otro proveedor");
        }
        return toResponse(provider);
    }

    private ProviderResponse toResponse(Provider provider) {
        List<ProviderResponse.BusinessSummary> businesses = businessRepository.findByProviderId(provider.getId()).stream()
                .map(b -> new ProviderResponse.BusinessSummary(b.getId(), b.getName()))
                .toList();

        return ProviderResponse.builder()
                .providerId(provider.getId())
                .userId(provider.getUserId())
                .businesses(businesses)
                .build();
    }
}
