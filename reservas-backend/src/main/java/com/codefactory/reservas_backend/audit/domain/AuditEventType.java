package com.codefactory.reservas_backend.audit.domain;

/**
 * Catálogo de eventos críticos a auditar, según la lista propuesta en
 * Sprint_1_ArquisuaveBD.docx (Paso 12) y confirmada en
 * interfaces-modulos-sprint-1.md (sección 4, "Audit Service"). Solo
 * REGISTRO_USUARIO se usa desde HU-01; el resto queda declarado para que
 * HU02-HU06 lo reutilicen sin tener que renegociar el catálogo.
 */
public enum AuditEventType {
    REGISTRO_USUARIO,
    LOGIN,
    LOGOUT,
    CAMBIO_ROL,
    CAMBIO_PERMISO,
    ACTIVACION_MFA,
    OPERACION_SENSIBLE
}
