package com.codefactory.reservas_backend.provider.domain;

/**
 * HU-07: tipos de negocio permitidos (lista cerrada; el AC rechaza "Panadería" y "123"). Supuesto D7 del
 * plan del Sprint 3, a confirmar con Calidad/PO. Debe coincidir con el CHECK ck_businesses_type (V13).
 */
public enum BusinessType {
    SALUD,
    BELLEZA,
    BIENESTAR,
    DEPORTE,
    EDUCACION,
    CONSULTORIA,
    ENTRETENIMIENTO,
    OTRO
}
