package com.codefactory.reservas_backend.identity.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Catálogo de roles. HU-01 solo usa CLIENTE, pero se deja PROVEEDOR y
 * ADMINISTRADOR ya definidos porque HU03 y HU05 (fuera del alcance de Simon
 * en Sprint 1) los reutilizarán directamente sobre esta misma tabla.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 30)
    private RoleName name;
}
