package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.resource.application.ResourceInfo;
import com.codefactory.reservas_backend.resource.application.ResourceLookupService;
import com.codefactory.reservas_backend.service.controller.dto.ServiceResourcesDtos.AssignedResource;
import com.codefactory.reservas_backend.service.controller.dto.ServiceResourcesDtos.ServiceResourcesResponse;
import com.codefactory.reservas_backend.service.domain.InvalidResourceAssignmentException;
import com.codefactory.reservas_backend.service.domain.ServiceNotFoundException;
import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.domain.ServiceResource;
import com.codefactory.reservas_backend.service.domain.ServiceResourceId;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import com.codefactory.reservas_backend.service.infrastructure.ServiceResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceResourceAssignmentServiceImpl implements ServiceResourceAssignmentService {

    static final String INVALID_RESOURCES_MESSAGE =
            "Uno o más recursos no existen o no pertenecen al negocio del servicio";

    private final ServiceOfferingRepository serviceRepository;
    private final ServiceResourceRepository assignmentRepository;
    private final BusinessAccessService businessAccessService;
    private final ResourceLookupService resourceLookupService;
    private final AuditService auditService;

    @Override
    @Transactional
    public ServiceResourcesResponse replaceResources(UUID serviceId, List<UUID> resourceIds,
                                                     UserIdentity requester, String originIp) {
        // Bloqueo de fila: dos PUT simultáneos sobre el mismo servicio se ejecutan uno tras otro.
        ServiceOffering service = serviceRepository.findByIdForUpdate(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException("El servicio solicitado no existe"));
        businessAccessService.requireOwner(service.getBusinessId(), requester);

        Set<UUID> wanted = new LinkedHashSet<>(resourceIds);
        List<ResourceInfo> valid = resourceLookupService.findInBusiness(service.getBusinessId(), wanted);
        if (valid.size() != wanted.size()) {
            // Atómico: no se cambia nada si aunque sea uno es inexistente o de otro negocio.
            throw new InvalidResourceAssignmentException(INVALID_RESOURCES_MESSAGE);
        }

        // Se aplica solo la diferencia: evita borrar y volver a insertar la misma clave en un flush.
        Set<UUID> current = new HashSet<>();
        assignmentRepository.findByIdServiceId(serviceId).forEach(a -> current.add(a.getId().getResourceId()));

        List<ServiceResourceId> toRemove = current.stream()
                .filter(id -> !wanted.contains(id))
                .map(id -> new ServiceResourceId(serviceId, id))
                .toList();
        List<ServiceResource> toAdd = wanted.stream()
                .filter(id -> !current.contains(id))
                .map(id -> new ServiceResource(serviceId, id))
                .toList();
        assignmentRepository.deleteAllById(toRemove);
        assignmentRepository.saveAll(toAdd);
        assignmentRepository.flush();

        if (!toRemove.isEmpty() || !toAdd.isEmpty()) {
            auditService.registerEvent(AuditEventType.ASIGNACION_RECURSOS, requester.email(), "SUCCESS",
                    "Servicio " + serviceId + ": " + wanted.size() + " recurso(s) asignado(s) (+" + toAdd.size()
                            + " / -" + toRemove.size() + ")", originIp);
        }
        return toResponse(serviceId, valid);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceResourcesResponse getResources(UUID serviceId, UserIdentity requester) {
        ServiceOffering service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException("El servicio solicitado no existe"));
        businessAccessService.requireOwner(service.getBusinessId(), requester);

        List<UUID> ids = assignmentRepository.findByIdServiceId(serviceId).stream()
                .map(a -> a.getId().getResourceId())
                .toList();
        return toResponse(serviceId, resourceLookupService.findByIds(ids));
    }

    private static ServiceResourcesResponse toResponse(UUID serviceId, List<ResourceInfo> resources) {
        List<AssignedResource> items = resources.stream()
                .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name()))
                .map(r -> new AssignedResource(r.id(), r.name(), r.type(), r.active()))
                .toList();
        return new ServiceResourcesResponse(serviceId, items);
    }
}
