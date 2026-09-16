package com.codefactory.reservas_backend.provider.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Información básica del negocio asociado a un Provider (HU-03). El modelo
 * permite 0..N negocios por proveedor (01_modelo_conceptual.md: "MVP:
 * normalmente 1, se deja abierto a N"); HU-03 solo crea el primero durante
 * el registro.
 */
@Entity
@Table(name = "businesses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
