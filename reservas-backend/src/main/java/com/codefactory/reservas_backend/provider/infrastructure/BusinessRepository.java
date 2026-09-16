package com.codefactory.reservas_backend.provider.infrastructure;

import com.codefactory.reservas_backend.provider.domain.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
    List<Business> findByProviderId(UUID providerId);
}
