package com.codefactory.reservas_backend.common.web;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.audit.domain.AuditLog;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OWASP-03: detrás de un proxy (Render) la IP del cliente sale de X-Forwarded-For, no es la del proxy. Con
 * MockMvc no interviene Tomcat, así que esta prueba usa un servidor real en un puerto aleatorio. La petición
 * viene de 127.0.0.1, que Tomcat trata como proxy interno de confianza (igual que el proxy privado de Render).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForwardedHeadersIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private HttpResponse<String> login(String email, String forwardedFor) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"incorrecta\"}"));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String originIpOfLastLoginAudit(String email) {
        List<AuditLog> events = auditLogRepository.findAll().stream()
                .filter(e -> e.getEventType() == AuditEventType.LOGIN && email.equalsIgnoreCase(e.getSubjectEmail()))
                .toList();
        assertThat(events).as("el intento de login de %s debe quedar auditado", email).isNotEmpty();
        return events.get(events.size() - 1).getOriginIp();
    }

    @Test
    void laAuditoriaRegistraLaIpReenviadaPorElProxy() throws Exception {
        String email = uniqueEmail("proxy");

        HttpResponse<String> response = login(email, "198.51.100.23");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(originIpOfLastLoginAudit(email)).isEqualTo("198.51.100.23");
    }

    @Test
    void conVariosProxiesSeToma_laUltimaIpNoConfiable() throws Exception {
        // «cliente real, proxy interno»: el cliente real es el primero que no es proxy de confianza.
        String email = uniqueEmail("cadena");

        login(email, "203.0.113.50, 10.0.0.7");

        assertThat(originIpOfLastLoginAudit(email)).isEqualTo("203.0.113.50");
    }

    @Test
    void sinLaCabeceraSeUsaLaIpDeLaConexion() throws Exception {
        String email = uniqueEmail("directo");

        login(email, null);

        assertThat(originIpOfLastLoginAudit(email)).isIn("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");
    }
}
