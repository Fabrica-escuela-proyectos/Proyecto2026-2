package com.codefactory.reservas_backend.audit.service;

import com.codefactory.reservas_backend.audit.model.AuditEventType;
import com.codefactory.reservas_backend.audit.model.AuditLog;
import com.codefactory.reservas_backend.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
 
/**
 * Punto unico de escritura de auditoria. Ningun otro componente debe
 * escribir directamente en audit_logs, para garantizar que la regla de "no
 * contrasenas/tokens en el log" se cumpla en un solo lugar.
 */
@Service
@RequiredArgsConstructor
public class AuditService {
 
    private final AuditLogRepository auditLogRepository;
 
    public void record(AuditEventType eventType, String subjectEmail, String outcome, String detail, String originIp) {
        AuditLog log = AuditLog.builder()
                .eventType(eventType)
                .subjectEmail(subjectEmail)
                .outcome(outcome)
                .detail(detail)
                .originIp(originIp)
                .build();
        auditLogRepository.save(log);
    }
}
