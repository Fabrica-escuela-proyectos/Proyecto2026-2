package com.codefactory.reservas_backend.identity.infrastructure;

import com.codefactory.reservas_backend.identity.domain.Mfa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MfaRepository extends JpaRepository<Mfa, UUID> {
    Optional<Mfa> findByUserId(UUID userId);
}
