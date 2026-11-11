package com.codefactory.reservas_backend.notification.application;

/** Un correo de texto plano para una persona. Texto plano a propósito: nada de HTML que escapar ni inyectar. */
public record EmailMessage(String toEmail, String toName, String subject, String text) {
}
