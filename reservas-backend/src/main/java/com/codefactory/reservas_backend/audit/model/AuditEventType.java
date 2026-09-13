package com.codefactory.reservas_backend.audit.model;

/**
 * Catalogo de eventos criticos a auditar, segun la lista propuesta en
 * Sprint_1_ArquisuaveBD.docx (Paso 12). Solo REGISTRO_USUARIO se usa desde
 * HU-01; el resto queda declarado para que HU02-HU06 lo reutilicen sin tener
 * que renegociar el catalogo.
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
