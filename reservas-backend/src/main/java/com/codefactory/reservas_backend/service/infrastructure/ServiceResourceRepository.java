package com.codefactory.reservas_backend.service.infrastructure;

import com.codefactory.reservas_backend.service.domain.ServiceResource;
import com.codefactory.reservas_backend.service.domain.ServiceResourceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceResourceRepository extends JpaRepository<ServiceResource, ServiceResourceId> {

    List<ServiceResource> findByIdServiceId(UUID serviceId);
}
