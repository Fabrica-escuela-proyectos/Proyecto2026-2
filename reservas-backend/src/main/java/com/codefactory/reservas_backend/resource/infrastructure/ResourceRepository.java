package com.codefactory.reservas_backend.resource.infrastructure;

import com.codefactory.reservas_backend.resource.domain.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    List<Resource> findByBusinessIdOrderByCreatedAtAsc(UUID businessId);

    List<Resource> findByBusinessIdAndIdIn(UUID businessId, Collection<UUID> ids);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);
}
