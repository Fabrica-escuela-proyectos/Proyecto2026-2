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

    /** HU-08: horas mínimas entre "ahora" y el inicio de una reserva nueva (1..720, por defecto 1). */
    @Column(name = "min_advance_hours", nullable = false)
    @Builder.Default
    private int minAdvanceHours = 1;

    /** HU-07: datos opcionales del negocio que ven los clientes en el catálogo. */
    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "address", length = 200)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", length = 30)
    private BusinessType businessType;

    /** 10 dígitos: celular (3...) o fijo nacional (60...); lo valida también un CHECK de la base (V13). */
    @Column(name = "phone", length = 10)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
