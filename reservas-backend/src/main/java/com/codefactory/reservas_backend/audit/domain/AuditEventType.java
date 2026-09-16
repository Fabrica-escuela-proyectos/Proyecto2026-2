package com.codefactory.reservas_backend.audit.domain;

/**
 * Catálogo de eventos críticos a auditar, según la lista propuesta en
 * Sprint_1_ArquisuaveBD.docx (Paso 12) y confirmada en
 * interfaces-modulos-sprint-1.md (sección 4, "Audit Service"). Desde HU02 en
 * adelante, cada evento se registra con este tipo más un "outcome"
 * (SUCCESS/REJECTED/PENDING, ver AuditService) en vez de tener una variante
 * de enum por cada resultado — se mantiene el mismo patrón que ya usaba
 * REGISTRO_USUARIO en HU-01.
 *
 * REGISTRO_PROVEEDOR y ELIMINACION_USUARIO se agregaron para HU03 y HU05
 * respectivamente porque no son variantes de un evento existente.
 */
public enum AuditEventType {
    REGISTRO_USUARIO,
    REGISTRO_PROVEEDOR,
    LOGIN,
    LOGOUT,
    CAMBIO_ROL,
    CAMBIO_PERMISO,
    ELIMINACION_USUARIO,
    ACTIVACION_MFA,
    ACCESO_DENEGADO,
    OPERACION_SENSIBLE
}
