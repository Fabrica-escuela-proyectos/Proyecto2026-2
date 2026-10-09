package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.domain.Business;
import com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import com.codefactory.reservas_backend.provider.infrastructure.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessAccessServiceImpl implements BusinessAccessService {

    private final BusinessRepository businessRepository;
    private final ProviderRepository providerRepository;

    @Override
    public void requireOwner(UUID businessId, UserIdentity requester) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessNotFoundException("El negocio solicitado no existe"));

        boolean isOwner = providerRepository.findById(business.getProviderId())
                .map(provider -> provider.getUserId().equals(requester.id()))
                .orElse(false);
        if (!isOwner) {
            // Ni siquiera un ADMINISTRADOR gestiona el catálogo de otro negocio (HU-09).
            throw new AccessDeniedException("No tiene acceso a un negocio ajeno");
        }
    }
}
