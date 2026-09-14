package com.codefactory.reservas_backend.identity.infrastructure;

import com.codefactory.reservas_backend.identity.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Preparación para HU02/HU04 (ver Session.java). No tiene consumidores en
 * Sprint 1.
 */
public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findByTokenId(String tokenId);
}
