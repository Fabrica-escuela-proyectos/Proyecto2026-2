package com.codefactory.reservas_backend.audit.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de auditoría de acciones críticas (Lineamientos Sec. 6.2 y 3.1;
 * arquitectura-sprint-1.md sección 6.8 "Módulo de Auditoría").
 *
 * Regla de seguridad NO negociable: esta entidad nunca debe recibir
 * contraseñas, tokens completos ni secretos. AuditService (audit.application)
 * es el único punto de escritura permitido y solo acepta los campos
 * definidos aquí.
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
