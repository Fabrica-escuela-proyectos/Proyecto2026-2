package com.codefactory.reservas_backend.provider.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Extiende a un User (identity) con la información propia del rol
 * Proveedor (HU-03). Referencia a identity.domain.User únicamente por su id
 * ({@code userId}), no mediante una relación JPA @ManyToOne/@OneToOne: así
 * el módulo provider no necesita importar la entidad interna de identity
 * (ADR-003-modularidad-e-interfaces.md), igual que identity.domain.Session
 * ya referencia su usuario solo por UUID.
 */
@Entity
@Table(name = "providers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
