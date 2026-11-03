package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.resource.controller.dto.CreateResourceRequest;
import com.codefactory.reservas_backend.resource.controller.dto.ResourceResponse;
import com.codefactory.reservas_backend.resource.controller.dto.UpdateResourceRequest;
import com.codefactory.reservas_backend.resource.domain.DuplicateResourceNameException;
import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.domain.ResourceNotFoundException;
import com.codefactory.reservas_backend.resource.domain.ResourceType;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    @Override
    @Transactional
    public ResourceResponse update(UUID resourceId, UpdateResourceRequest request, UserIdentity requester, String originIp) {
        // Bloqueo de fila: dos ediciones simultáneas del mismo recurso se aplican una tras otra.
        Resource resource = resourceRepository.findByIdForUpdate(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("El recurso solicitado no existe"));
        businessAccessService.requireOwner(resource.getBusinessId(), requester);

        List<String> changes = new ArrayList<>();
        if (request.getName() != null) {
            String name = request.getName().trim();
            if (!name.equals(resource.getName())) {
                if (resourceRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(resource.getBusinessId(), name, resourceId)) {
                    throw new DuplicateResourceNameException("Ya existe un recurso con ese nombre en el negocio");
                }
                changes.add("nombre");
                resource.setName(name);
            }
        }
        if (request.getType() != null) {
            ResourceType type = ResourceType.valueOf(request.getType().trim().toUpperCase(Locale.ROOT));
            if (type != resource.getType()) {
                changes.add("tipo");
                resource.setType(type);
            }
        }

        if (!changes.isEmpty()) {
            // saveAndFlush: el índice único uk_resources_business_name defiende la carrera de dos ediciones (409).
            resourceRepository.saveAndFlush(resource);
            auditService.registerEvent(AuditEventType.EDICION_RECURSO, requester.email(), "SUCCESS",
                    "Recurso " + resourceId + " editado (" + String.join(", ", changes) + ")", originIp);
        }
        return toResponse(resource);
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
