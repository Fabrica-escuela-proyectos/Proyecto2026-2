package com.codefactory.reservas_backend.provider.infrastructure;

import com.codefactory.reservas_backend.provider.domain.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProviderRepository extends JpaRepository<Provider, UUID> {
    Optional<Provider> findByUserId(UUID userId);
}
