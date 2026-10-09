package com.codefactory.reservas_backend.resource.infrastructure;

import com.codefactory.reservas_backend.resource.domain.Resource;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    List<Resource> findByBusinessIdOrderByCreatedAtAsc(UUID businessId);

    List<Resource> findByBusinessIdAndIdIn(UUID businessId, Collection<UUID> ids);

    /** Bloquea la fila del recurso hasta el fin de la transacción: serializa ediciones concurrentes de su horario. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id")
    Optional<Resource> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);
}
