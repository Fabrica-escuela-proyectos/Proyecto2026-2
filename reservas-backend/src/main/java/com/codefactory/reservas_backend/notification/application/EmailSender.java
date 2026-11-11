package com.codefactory.reservas_backend.notification.application;

/**
 * Puerto de salida para enviar correos (ADR-009). Implementaciones: Brevo por HTTPS en producción y una que solo
 * registra en el log cuando no hay clave (desarrollo, pruebas o despliegue sin configurar).
 */
public interface EmailSender {

    /** @throws EmailDeliveryException si el proveedor rechaza o no responde (nunca incluye la clave en el mensaje) */
    void send(EmailMessage message);
}
