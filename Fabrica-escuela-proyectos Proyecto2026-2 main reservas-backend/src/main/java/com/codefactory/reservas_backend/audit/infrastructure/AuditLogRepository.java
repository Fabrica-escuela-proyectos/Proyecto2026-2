package com.codefactory.reservas_backend.audit.infrastructure;

import com.codefactory.reservas_backend.audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Acceso a PostgreSQL para audit_logs (capa infrastructure según
 * arquitectura-sprint-1.md sección 7.1: "Repositorios y acceso a
 * PostgreSQL"). Solo debe ser usado por audit.application.AuditServiceImpl.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
