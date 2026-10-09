package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.resource.controller.dto.CreateResourceRequest;
import com.codefactory.reservas_backend.resource.controller.dto.ResourceResponse;
import com.codefactory.reservas_backend.resource.domain.DuplicateResourceNameException;
import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.domain.ResourceType;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final ResourceRepository resourceRepository;
    private final BusinessAccessService businessAccessService;
    private final AuditService auditService;

    @Override
    @Transactional
    public ResourceResponse create(UUID businessId, CreateResourceRequest request, UserIdentity requester, String originIp) {
        businessAccessService.requireOwner(businessId, requester);

        String name = request.getName().trim();
        if (resourceRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new DuplicateResourceNameException("Ya existe un recurso con ese nombre en el negocio");
        }

        // saveAndFlush: si dos registros simultáneos pasan la verificación anterior, el
        // índice único uk_resources_business_name falla aquí y se traduce a 409.
        Resource saved = resourceRepository.saveAndFlush(Resource.builder()
                .businessId(businessId)
                .name(name)
                .type(ResourceType.valueOf(request.getType().trim().toUpperCase(Locale.ROOT)))
                .active(true)
                .createdBy(requester.id())
                .build());

        auditService.registerEvent(AuditEventType.REGISTRO_RECURSO, requester.email(), "SUCCESS",
                "Recurso " + saved.getId() + " registrado en el negocio " + businessId, originIp);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceResponse> list(UUID businessId, UserIdentity requester) {
        businessAccessService.requireOwner(businessId, requester);
        return resourceRepository.findByBusinessIdOrderByCreatedAtAsc(businessId).stream()
                .map(this::toResponse)
                .toList();
    }

    private ResourceResponse toResponse(Resource r) {
        return ResourceResponse.builder()
                .id(r.getId())
                .businessId(r.getBusinessId())
                .name(r.getName())
                .type(r.getType().name())
                .active(r.isActive())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
