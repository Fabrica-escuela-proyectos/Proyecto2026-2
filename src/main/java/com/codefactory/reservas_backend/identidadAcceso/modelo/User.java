package com.codefactory.reservas_backend.identidadAcceso.modelo;

import jakarta.persistence.*;
import lombok.*;
 
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
 
/**
 * Entidad de usuario para el modulo Identity and Access.
 *
 * Fuente de las columnas y restricciones: MatrizProvisionalHU.docx (fila HU01,
 * tabla "users") y HU-01-Registrar-cliente.txt (email/celular unicos,
 * contrasena nunca en texto plano). El modelo formal (conceptual/logico/
 * fisico) es responsabilidad de Andraus en Bases de Datos: esta entidad debe
 * validarse contra ese modelo antes de darla por definitiva (ver
 * docs/matriz-actualizaciones.md).
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
    private String phoneNumber;
 
    /**
     * Nunca almacenar la contrasena en texto plano. Escenario Gherkin
     * "La informacion del usuario queda protegida en todo momento": el hash
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
