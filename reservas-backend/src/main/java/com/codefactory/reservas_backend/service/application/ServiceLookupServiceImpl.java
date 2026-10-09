package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import com.codefactory.reservas_backend.service.infrastructure.ServiceResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceLookupServiceImpl implements ServiceLookupService {

    private final ServiceOfferingRepository serviceRepository;
    private final ServiceResourceRepository assignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceInfo> findById(UUID serviceId) {
        return serviceRepository.findById(serviceId).map(ServiceLookupServiceImpl::toInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> assignedResourceIds(UUID serviceId) {
        return assignmentRepository.findByIdServiceId(serviceId).stream()
                .map(a -> a.getId().getResourceId())
                .toList();
    }

    private static ServiceInfo toInfo(ServiceOffering s) {
        return new ServiceInfo(s.getId(), s.getBusinessId(), s.getName(), s.getDurationMinutes(), s.getPriceCop(), s.isActive());
    }
}
