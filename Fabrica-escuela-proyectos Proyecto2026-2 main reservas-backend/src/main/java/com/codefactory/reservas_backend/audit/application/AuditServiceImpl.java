package com.codefactory.reservas_backend.audit.application;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.audit.domain.AuditLog;
import com.codefactory.reservas_backend.audit.infrastructure.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Único punto de escritura de auditoría. Coordina el caso de uso
 * "registrar evento" apoyándose en la persistencia de infrastructure
 * (arquitectura-sprint-1.md sección 7.1: "Application: coordina los pasos
 * necesarios para ejecutar un caso de uso").
 */
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    public void registerEvent(AuditEventType eventType, String subjectIdentifier, String outcome, String detail, String originIp) {
        AuditLog log = AuditLog.builder()
                .eventType(eventType)
                .subjectEmail(subjectIdentifier)
                .outcome(outcome)
                .detail(detail)
                .originIp(originIp)
                .build();
        auditLogRepository.save(log);
    }
}
