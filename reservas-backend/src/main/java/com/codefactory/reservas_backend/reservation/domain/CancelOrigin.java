package com.codefactory.reservas_backend.reservation.domain;

/**
 * Origen de la cancelación de una reserva (regla acordada en el plan, sección 6). Las
 * cancelaciones que no decide el cliente no están sujetas a la regla de antelación de 1 hora.
 */
public enum CancelOrigin {
    CLIENTE,
    PROVEEDOR,
    ELIMINACION_CUENTA,
    RECURSO_NO_DISPONIBLE,
    SERVICIO_NO_DISPONIBLE
}
