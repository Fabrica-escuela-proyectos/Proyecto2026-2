package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.service.domain.ServiceNotFoundException;
import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceLifecycleServiceImpl implements ServiceLifecycleService {

    private final ServiceOfferingRepository serviceRepository;
    private final BusinessAccessService businessAccessService;
    private final AuditService auditService;

    @Override
    @Transactional
    public ServiceInfo lockOwnedService(UUID serviceId, UserIdentity requester) {
        return toInfo(lockOwned(serviceId, requester));
    }

    @Override
    @Transactional
    public void deactivate(UUID serviceId, UserIdentity requester, int cancelledBookings, String originIp) {
        ServiceOffering service = lockOwned(serviceId, requester);
        service.setActive(false);
        serviceRepository.saveAndFlush(service);
        auditService.registerEvent(AuditEventType.DESACTIVACION_SERVICIO, requester.email(), "SUCCESS",
                "Servicio " + serviceId + " desactivado (" + cancelledBookings + " reserva(s) futura(s) cancelada(s))", originIp);
    }

    @Override
    @Transactional
    public ServiceInfo reactivate(UUID serviceId, UserIdentity requester, String originIp) {
        ServiceOffering service = lockOwned(serviceId, requester);
        if (!service.isActive()) {
            // Mismo servicio y mismos datos (no se crea otro); las reservas canceladas al desactivarlo siguen canceladas.
            service.setActive(true);
            serviceRepository.saveAndFlush(service);
            auditService.registerEvent(AuditEventType.REACTIVACION_SERVICIO, requester.email(), "SUCCESS",
                    "Servicio " + serviceId + " reactivado", originIp);
        }
        return toInfo(service);
    }

    private ServiceOffering lockOwned(UUID serviceId, UserIdentity requester) {
        ServiceOffering service = serviceRepository.findByIdForUpdate(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException("El servicio solicitado no existe"));
        businessAccessService.requireOwner(service.getBusinessId(), requester);
        return service;
    }

    private static ServiceInfo toInfo(ServiceOffering s) {
        return new ServiceInfo(s.getId(), s.getBusinessId(), s.getName(), s.getDurationMinutes(), s.getPriceCop(), s.isActive());
    }
}
