package com.codefactory.reservas_backend.notification.infrastructure;

import com.codefactory.reservas_backend.notification.application.EmailDeliveryException;
import com.codefactory.reservas_backend.notification.application.EmailMessage;
import com.codefactory.reservas_backend.notification.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Envía el correo por la API transaccional de Brevo (HTTPS): Render gratuito bloquea SMTP (ADR-009).
 * La URL es fija (no depende de datos del usuario: sin riesgo de SSRF, OWASP A10) y la clave viaja solo en la
 * cabecera {@code api-key}; nunca se escribe en el log ni en las excepciones.
 *
 * No envía a dominios reservados para pruebas (RFC 2606/6761: example.com, .test, .invalid...): así las corridas de
 * Postman y las pruebas de Calidad contra Render no gastan el cupo diario ni generan rebotes.
 */
public class BrevoEmailSender implements EmailSender {

    static final URI ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");
    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);
    private static final Set<String> RESERVED_DOMAINS = Set.of("example.com", "example.org", "example.net");
    private static final List<String> RESERVED_SUFFIXES = List.of(".test", ".example", ".invalid", ".localhost");

    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String apiKey;
    private final String senderEmail;
    private final String senderName;

    public BrevoEmailSender(ObjectMapper objectMapper, String apiKey, String senderEmail, String senderName) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), objectMapper, ENDPOINT,
                apiKey, senderEmail, senderName);
    }

    BrevoEmailSender(HttpClient http, ObjectMapper objectMapper, URI endpoint, String apiKey, String senderEmail,
                     String senderName) {
        this.http = http;
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    @Override
    public void send(EmailMessage message) {
        if (isReserved(message.toEmail())) {
            log.info("Aviso no enviado: el destinatario es de un dominio reservado para pruebas");
            return;
        }
        Map<String, Object> to = message.toName() == null || message.toName().isBlank()
                ? Map.of("email", message.toEmail())
                : Map.of("email", message.toEmail(), "name", message.toName());
        Map<String, Object> body = Map.of(
                "sender", Map.of("email", senderEmail, "name", senderName),
                "to", List.of(to),
                "subject", message.subject(),
                "textContent", message.text());
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("api-key", apiKey)
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new EmailDeliveryException("Brevo no respondió: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailDeliveryException("Envío interrumpido", e);
        }
        if (response.statusCode() / 100 != 2) {
            // El cuerpo de error de Brevo trae el código (p. ej. unauthorized) y nunca la clave.
            throw new EmailDeliveryException("Brevo respondió " + response.statusCode() + ": " + abbreviate(response.body()));
        }
    }

    static boolean isReserved(String email) {
        String domain = email == null ? "" : email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        return RESERVED_DOMAINS.contains(domain) || RESERVED_SUFFIXES.stream().anyMatch(domain::endsWith)
                || domain.equals("localhost");
    }

    private static String abbreviate(String text) {
        return text == null ? "" : text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
}
