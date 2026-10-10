package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceLookupServiceImpl implements ResourceLookupService {

    private final ResourceRepository resourceRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ResourceInfo> findInBusiness(UUID businessId, Collection<UUID> resourceIds) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        return resourceRepository.findByBusinessIdAndIdIn(businessId, resourceIds).stream()
                .map(ResourceLookupServiceImpl::toInfo)
                .toList();
    }

    @Override
    @Transactional
    public boolean lockActive(UUID resourceId) {
        // Lectura directa de la base (no del contexto de persistencia): ver ResourceRepository#lockAndReadActive.
        return resourceRepository.lockAndReadActive(resourceId).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceInfo> findByIds(Collection<UUID> resourceIds) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        return resourceRepository.findAllById(resourceIds).stream()
                .map(ResourceLookupServiceImpl::toInfo)
                .sorted(Comparator.comparing(ResourceInfo::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static ResourceInfo toInfo(Resource r) {
        return new ResourceInfo(r.getId(), r.getBusinessId(), r.getName(), r.getType().name(), r.isActive());
    }
}
