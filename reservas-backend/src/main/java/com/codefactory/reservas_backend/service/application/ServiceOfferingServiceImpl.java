package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.service.controller.dto.CreateServiceRequest;
import com.codefactory.reservas_backend.service.controller.dto.ServiceResponse;
import com.codefactory.reservas_backend.service.controller.dto.UpdateServiceRequest;
import com.codefactory.reservas_backend.service.domain.DuplicateServiceNameException;
import com.codefactory.reservas_backend.service.domain.ServiceNotFoundException;
import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceOfferingServiceImpl implements ServiceOfferingService {

    private final ServiceOfferingRepository serviceRepository;
    private final BusinessAccessService businessAccessService;
    private final AuditService auditService;

    @Override
    @Transactional
    public ServiceResponse create(UUID businessId, CreateServiceRequest request, UserIdentity requester, String originIp) {
        businessAccessService.requireOwner(businessId, requester);

        String name = request.getName().trim();
        if (serviceRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new DuplicateServiceNameException("Ya existe un servicio con ese nombre en el negocio");
        }

        String description = request.getDescription() == null || request.getDescription().isBlank()
                ? null : request.getDescription().trim();
        // saveAndFlush: si dos creaciones simultáneas pasan la verificación anterior,
        // el índice único uk_services_business_name falla aquí y se traduce a 409.
        ServiceOffering saved = serviceRepository.saveAndFlush(ServiceOffering.builder()
                .businessId(businessId)
                .name(name)
                .description(description)
                .durationMinutes(request.getDurationMinutes())
                .priceCop(request.getPriceCop())
                .active(true)
                .createdBy(requester.id())
                .build());

        auditService.registerEvent(AuditEventType.CREACION_SERVICIO, requester.email(), "SUCCESS",
                "Servicio " + saved.getId() + " creado en el negocio " + businessId, originIp);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceResponse> list(UUID businessId, UserIdentity requester) {
        businessAccessService.requireOwner(businessId, requester);
        return serviceRepository.findByBusinessIdOrderByCreatedAtAsc(businessId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ServiceResponse update(UUID serviceId, UpdateServiceRequest request, UserIdentity requester, String originIp) {
        // Bloqueo de fila: serializa la edición con otras ediciones y con las reservas que se crean a la vez
        // (BookingServiceImpl toma un bloqueo compartido del servicio antes de leer su duración y precio).
        ServiceOffering service = serviceRepository.findByIdForUpdate(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException("El servicio solicitado no existe"));
        businessAccessService.requireOwner(service.getBusinessId(), requester);

        List<String> changes = new ArrayList<>();
        if (request.getName() != null) {
            String name = request.getName().trim();
            if (!name.equals(service.getName())) {
                if (serviceRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(service.getBusinessId(), name, serviceId)) {
                    throw new DuplicateServiceNameException("Ya existe un servicio con ese nombre en el negocio");
                }
                changes.add("nombre");
                service.setName(name);
            }
        }
        if (request.getDescription() != null) {
            String description = request.getDescription().isBlank() ? null : request.getDescription().trim();
            if (!Objects.equals(description, service.getDescription())) {
                changes.add("descripción");
                service.setDescription(description);
            }
        }
        if (request.getDurationMinutes() != null && request.getDurationMinutes() != service.getDurationMinutes()) {
            changes.add("duración " + service.getDurationMinutes() + " -> " + request.getDurationMinutes() + " min");
            service.setDurationMinutes(request.getDurationMinutes());
        }
        if (request.getPriceCop() != null && request.getPriceCop() != service.getPriceCop()) {
            changes.add("precio " + service.getPriceCop() + " -> " + request.getPriceCop() + " COP");
            service.setPriceCop(request.getPriceCop());
        }

        if (!changes.isEmpty()) {
            // El precio de las reservas existentes no cambia: bookings.price_cop es una copia (ADR-006).
            serviceRepository.saveAndFlush(service);
            auditService.registerEvent(AuditEventType.EDICION_SERVICIO, requester.email(), "SUCCESS",
                    "Servicio " + serviceId + " editado (" + String.join(", ", changes) + ")", originIp);
        }
        return toResponse(service);
    }

    private ServiceResponse toResponse(ServiceOffering s) {
        return ServiceResponse.builder()
                .id(s.getId())
                .businessId(s.getBusinessId())
                .name(s.getName())
                .description(s.getDescription())
                .durationMinutes(s.getDurationMinutes())
                .priceCop(s.getPriceCop())
                .active(s.isActive())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
