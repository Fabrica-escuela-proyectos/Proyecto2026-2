package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.domain.ResourceNotFoundException;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceLifecycleServiceImpl implements ResourceLifecycleService {

    private final ResourceRepository resourceRepository;
    private final BusinessAccessService businessAccessService;
    private final AuditService auditService;

    @Override
    @Transactional
    public ResourceInfo lockOwnedResource(UUID resourceId, UserIdentity requester) {
        return toInfo(lockOwned(resourceId, requester));
    }

    @Override
    @Transactional
    public void deactivate(UUID resourceId, UserIdentity requester, int cancelledBookings, String originIp) {
        Resource resource = lockOwned(resourceId, requester);
        resource.setActive(false);
        resourceRepository.saveAndFlush(resource);
        auditService.registerEvent(AuditEventType.DESACTIVACION_RECURSO, requester.email(), "SUCCESS",
                "Recurso " + resourceId + " desactivado (" + cancelledBookings + " reserva(s) futura(s) cancelada(s))", originIp);
    }

    @Override
    @Transactional
    public ResourceInfo reactivate(UUID resourceId, UserIdentity requester, String originIp) {
        Resource resource = lockOwned(resourceId, requester);
        if (!resource.isActive()) {
            resource.setActive(true);
            resourceRepository.saveAndFlush(resource);
            auditService.registerEvent(AuditEventType.REACTIVACION_RECURSO, requester.email(), "SUCCESS",
                    "Recurso " + resourceId + " reactivado", originIp);
        }
        return toInfo(resource);
    }

    private Resource lockOwned(UUID resourceId, UserIdentity requester) {
        Resource resource = resourceRepository.findByIdForUpdate(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("El recurso solicitado no existe"));
        businessAccessService.requireOwner(resource.getBusinessId(), requester);
        return resource;
    }

    private static ResourceInfo toInfo(Resource r) {
        return new ResourceInfo(r.getId(), r.getBusinessId(), r.getName(), r.getType().name(), r.isActive());
    }
}
