package com.codefactory.reservas_backend.resource.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Cierre puntual de un recurso (HU-21): el periodo [startAt, endAt) en que no atiende, aparte de su
 * horario semanal. La base impide cierres traslapados del mismo recurso (V14, EXCLUDE).
 */
@Entity
@Table(name = "resource_closures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "start_at", nullable = false, updatable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false, updatable = false)
    private Instant endAt;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
