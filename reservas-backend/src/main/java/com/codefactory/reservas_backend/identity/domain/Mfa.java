package com.codefactory.reservas_backend.identity.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Configuración de MFA (TOTP, RFC 6238) de un usuario. Requerida por
 * ADR-002-autenticacion-y-sesiones.md sección 8 ("MFA para administradores")
 * y por HU-05 ("se activa un evento obligatorio, para la creación de MFA
 * para el nuevo Administrador") y HU-02 ("Verificación adicional para
 * cuentas administrativas").
 *
 * Ciclo de vida: se crea con {@code enabled=false} y un secreto nuevo, ya
 * sea porque el usuario llamó a POST /api/v1/auth/mfa/setup, o porque fue
 * ascendido a ADMINISTRADOR (HU-05, alta obligatoria pero no auto-activada:
 * el usuario debe confirmar un código válido en /mfa/activate para que
 * {@code enabled} pase a true). Mientras esté en false, el login no exige
 * el código todavía (ver AuthServiceImpl) — evita un bloqueo permanente de
 * la cuenta recién ascendida.
 */
@Entity
@Table(name = "mfa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mfa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /**
     * Secreto TOTP en Base32 (RFC 4648, sin padding). Nunca se expone salvo
     * en la respuesta de /mfa/setup, una sola vez, mientras enabled=false.
     */
    @Column(name = "secret", nullable = false, length = 64)
    private String secret;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
