package com.codefactory.reservas_backend.audit.repository;

import com.codefactory.reservas_backend.audit.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
 
import java.util.UUID;
 
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
