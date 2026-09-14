package com.codefactory.reservas_backend.identity.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entidad de usuario para el módulo Identity & Access.
 *
 * Fuente de las columnas y restricciones: MatrizProvisionalHU.docx (fila
 * HU01, tabla "users"), HU-01-Registrar-cliente.txt (email/celular únicos,
 * contraseña nunca en texto plano) y dtos-sprint-1.md sección 2 (nombre del
 * campo de celular: {@code cellphone}).
 *
 * El nombre de campo Java se alineó a "cellphone" para que coincida
 * literalmente con el DTO de entrada (RegisterUserRequest) y evitar una
 * traducción de nombres innecesaria entre capas; la columna física se dejó
 * como {@code phone_number} porque ese nombre ya estaba propuesto en la
 * migración V1 y el modelo físico definitivo todavía es responsabilidad de
 * Andraus (BD) — ver docs/matriz-actualizaciones.md.
 */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_users_phone", columnNames = "phone_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String cellphone;

    /**
     * Nunca almacenar la contraseña en texto plano. Escenario Gherkin
     * "La información del usuario queda protegida en todo momento": el hash
     * debe ser irreversible (ver PasswordEncoderConfig - BCrypt).
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
