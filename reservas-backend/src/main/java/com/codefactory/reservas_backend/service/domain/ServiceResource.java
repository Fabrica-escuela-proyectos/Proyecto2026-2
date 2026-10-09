package com.codefactory.reservas_backend.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Asignación de un recurso a un servicio (HU-18): "en este recurso se presta
 * este servicio". El recurso se referencia solo por id (ADR-003).
 */
@Entity
@Table(name = "service_resources")
@Getter
@NoArgsConstructor
public class ServiceResource {

    @EmbeddedId
    private ServiceResourceId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ServiceResource(UUID serviceId, UUID resourceId) {
        this.id = new ServiceResourceId(serviceId, resourceId);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
