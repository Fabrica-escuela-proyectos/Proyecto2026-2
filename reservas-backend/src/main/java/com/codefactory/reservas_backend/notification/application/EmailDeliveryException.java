package com.codefactory.reservas_backend.notification.application;

/** El proveedor de correo no aceptó el mensaje. Nunca llega al cliente HTTP: los avisos van después de confirmar. */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
