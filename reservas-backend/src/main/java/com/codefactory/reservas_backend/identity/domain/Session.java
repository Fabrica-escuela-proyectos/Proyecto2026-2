package com.codefactory.reservas_backend.identity.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de control de sesión, tal como exige
 * ADR-002-autenticacion-y-sesiones.md sección 5 ("Revocación de sesiones"):
 * aunque la autenticación usa JWT, se mantiene una tabla de sesiones que
 * permite revocar el acceso antes de que el token expire por sí solo.
 *
 * IMPORTANTE — alcance real en Sprint 1: esta entidad y su tabla
 * (V2__create_sessions_table.sql) son preparación arquitectónica para
 * HU02 (Inicio de sesión) y HU04 (Cerrar sesión), que NO son tareas
 * individuales de Simon. Nada en este Sprint crea, lee o revoca sesiones
 * todavía — la clase existe para que quien implemente HU02/HU04 no tenga
 * que diseñar el modelo desde cero. No se persiste el JWT completo aquí
 * (ADR-002 sección 2: "no almacenará el JWT completo"), solo el
 * identificador del token (claim {@code jti}) necesario para revocar.
 */
@Entity
@Table(name = "sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Identificador del token (claim JWT {@code jti}), no el JWT completo.
     */
    @Column(name = "token_id", nullable = false, unique = true, length = 100)
    private String tokenId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public boolean isValid(Instant now) {
        return !revoked && now.isBefore(expiresAt);
    }
}
