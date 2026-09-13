package com.codefactory.reservas_backend.audit.model;

import jakarta.persistence.*;
import lombok.*;
 
import java.time.Instant;
import java.util.UUID;
 
/**
 * Registro de auditoria de acciones criticas (Lineamientos Sec. 6.2 y 3.1).
 *
 * Regla de seguridad NO negociable: esta entidad nunca debe recibir
 * contrasenas, tokens completos ni secretos. AuditService es el unico punto
 * de escritura y solo acepta los campos definidos aqui.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
 
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private AuditEventType eventType;
 
    @Column(name = "subject_email", length = 150)
    private String subjectEmail;
 
    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome; // SUCCESS | REJECTED
 
    @Column(name = "detail", length = 300)
    private String detail;
 
    @Column(name = "origin_ip", length = 45)
    private String originIp;
 
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
 
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
