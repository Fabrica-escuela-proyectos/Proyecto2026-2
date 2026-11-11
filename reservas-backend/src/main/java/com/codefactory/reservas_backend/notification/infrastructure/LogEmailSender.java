package com.codefactory.reservas_backend.notification.infrastructure;

import com.codefactory.reservas_backend.notification.application.EmailMessage;
import com.codefactory.reservas_backend.notification.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Se usa cuando no hay {@code BREVO_API_KEY} (desarrollo, pruebas o un despliegue sin configurar): no envía nada,
 * deja una línea en el log con el destinatario enmascarado y guarda los últimos mensajes en memoria (acotados)
 * para que las pruebas de integración comprueben qué se habría enviado.
 */
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);
    static final int MAX_KEPT = 200;

    private final Deque<EmailMessage> recent = new ArrayDeque<>();

    @Override
    public void send(EmailMessage message) {
        log.info("Correo no enviado (sin proveedor configurado) para {}: «{}»", mask(message.toEmail()), message.subject());
        synchronized (recent) {
            recent.addLast(message);
            if (recent.size() > MAX_KEPT) {
                recent.removeFirst();
            }
        }
    }

    /** Copia de los últimos mensajes «enviados», del más viejo al más nuevo. */
    public List<EmailMessage> recent() {
        synchronized (recent) {
            return new ArrayList<>(recent);
        }
    }

    /** {@code ana.perez@dominio.com} → {@code a***@dominio.com}: el log no guarda correos completos. */
    static String mask(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
