package com.codefactory.reservas_backend.audit.application;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;

/**
 * Contrato expuesto por el módulo Audit al resto del monolito
 * (interfaces-modulos-sprint-1.md sección 4 y ADR-003-modularidad-e-interfaces.md).
 * Ningún otro módulo debe escribir directamente en audit_logs: siempre a
 * través de esta interfaz, nunca contra AuditLogRepository directamente.
 *
 * Nota de implementación: el contrato conceptual del documento de
 * interfaces es {@code registerEvent(String eventType, Long userId)}. Se
 * amplió deliberadamente a los parámetros que HU-01 y
 * errores-api-sprint-1.md ya exigen en la práctica (identificador del
 * sujeto por email —no todos los eventos tienen un userId todavía—,
 * resultado de la operación, detalle y origen), conservando el nombre del
 * método. Ver docs/matriz-actualizaciones.md.
 */
public interface AuditService {

    void registerEvent(AuditEventType eventType, String subjectIdentifier, String outcome, String detail, String originIp);
}
