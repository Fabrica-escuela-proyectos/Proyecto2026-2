package com.codefactory.reservas_backend.notification.infrastructure;

import com.codefactory.reservas_backend.notification.application.EmailDeliveryException;
import com.codefactory.reservas_backend.notification.application.EmailMessage;
import com.codefactory.reservas_backend.notification.application.EmailSender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BrevoEmailSender contra un servidor HTTP local que imita la API de Brevo (no se llama a Brevo de verdad),
 * LogEmailSender y la elección entre ambos en NotificationConfig.
 */
class EmailSendersTest {

    private static final String FAKE_KEY = "clave-falsa-de-prueba";
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final List<String[]> requests = new ArrayList<>();
    private final AtomicInteger status = new AtomicInteger(201);
    private HttpServer server;
    private BrevoEmailSender brevo;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new String[]{exchange.getRequestMethod(), exchange.getRequestHeaders().getFirst("api-key"), body});
            byte[] answer = (status.get() == 201 ? "{\"messageId\":\"<1@brevo>\"}" : "{\"code\":\"unauthorized\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        server.start();
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email");
        brevo = new BrevoEmailSender(HttpClient.newHttpClient(), mapper, endpoint, FAKE_KEY, "avisos@reservas.co", "Reservas");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static EmailMessage message(String to) {
        return new EmailMessage(to, "Ana", "Tu reserva fue cancelada", "Hola Ana");
    }

    @Test
    void brevoRecibeUnPostConLaClaveEnLaCabeceraYElCorreoEnJson() throws Exception {
        brevo.send(message("ana@correo.co"));

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0)[0]).isEqualTo("POST");
        assertThat(requests.get(0)[1]).isEqualTo(FAKE_KEY);
        JsonNode body = mapper.readTree(requests.get(0)[2]);
        assertThat(body.get("sender").get("email").asText()).isEqualTo("avisos@reservas.co");
        assertThat(body.get("to").get(0).get("email").asText()).isEqualTo("ana@correo.co");
        assertThat(body.get("to").get(0).get("name").asText()).isEqualTo("Ana");
        assertThat(body.get("subject").asText()).isEqualTo("Tu reserva fue cancelada");
        assertThat(body.get("textContent").asText()).isEqualTo("Hola Ana");
    }

    @Test
    void unaRespuestaDeErrorLanzaExcepcionSinLaClave() {
        status.set(401);

        assertThatThrownBy(() -> brevo.send(message("ana@correo.co")))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("401")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(FAKE_KEY));
    }

    @Test
    void sinServidorLanzaExcepcionDeEntrega() {
        server.stop(0);

        assertThatThrownBy(() -> brevo.send(message("ana@correo.co"))).isInstanceOf(EmailDeliveryException.class);
    }

    @Test
    void noEnviaADominiosReservadosParaPruebas() {
        for (String to : new String[]{"qa.cli.x@example.com", "a@EXAMPLE.ORG", "b@sitio.test", "c@x.invalid", "d@localhost"}) {
            brevo.send(message(to));
        }

        assertThat(requests).isEmpty();
        assertThat(BrevoEmailSender.isReserved("ana@correo.co")).isFalse();
        assertThat(BrevoEmailSender.isReserved("ana@examples.com")).isFalse();
    }

    @Test
    void elModoLogGuardaLosUltimosMensajesYEnmascaraElCorreo() {
        LogEmailSender log = new LogEmailSender();
        for (int i = 0; i < LogEmailSender.MAX_KEPT + 5; i++) {
            log.send(message("u" + i + "@correo.co"));
        }

        assertThat(log.recent()).hasSize(LogEmailSender.MAX_KEPT);
        assertThat(log.recent().get(0).toEmail()).isEqualTo("u5@correo.co");
        assertThat(LogEmailSender.mask("ana.perez@correo.co")).isEqualTo("a***@correo.co");
        assertThat(LogEmailSender.mask("sinarroba")).isEqualTo("***");
    }

    @Test
    void laConfiguracionEligeBrevoSoloConClaveYRemitente() {
        NotificationConfig config = new NotificationConfig();

        EmailSender none = config.emailSender(mapper, "", "avisos@reservas.co", "R");
        EmailSender noSender = config.emailSender(mapper, FAKE_KEY, " ", "R");
        EmailSender full = config.emailSender(mapper, FAKE_KEY, "avisos@reservas.co", "R");

        assertThat(none).isInstanceOf(LogEmailSender.class);
        assertThat(noSender).isInstanceOf(LogEmailSender.class);
        assertThat(full).isInstanceOf(BrevoEmailSender.class);
    }
}
