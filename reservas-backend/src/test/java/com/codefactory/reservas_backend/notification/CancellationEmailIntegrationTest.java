package com.codefactory.reservas_backend.notification;

import com.codefactory.reservas_backend.notification.application.EmailMessage;
import com.codefactory.reservas_backend.notification.application.EmailSender;
import com.codefactory.reservas_backend.notification.infrastructure.LogEmailSender;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * P5 de Calidad (ADR-009): el cliente recibe un correo cuando le cancelan una reserva sin que él lo pida. En pruebas
 * no hay BREVO_API_KEY, así que el correo queda en {@link LogEmailSender} y se lee de ahí. El envío es asíncrono y
 * ocurre después de confirmar la transacción: se espera hasta 5 s.
 */
class CancellationEmailIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EmailSender emailSender;

    private List<EmailMessage> mailsTo(String email) throws InterruptedException {
        LogEmailSender log = (LogEmailSender) emailSender;
        for (int i = 0; i < 50; i++) {
            List<EmailMessage> found = log.recent().stream().filter(m -> m.toEmail().equalsIgnoreCase(email)).toList();
            if (!found.isEmpty()) {
                return found;
            }
            Thread.sleep(100);
        }
        return List.of();
    }

    /** Para afirmar que algo NO se envió: espera lo mismo que tardaría un envío. */
    private List<EmailMessage> mailsAfterWait(String email) throws InterruptedException {
        Thread.sleep(1500);
        return ((LogEmailSender) emailSender).recent().stream().filter(m -> m.toEmail().equalsIgnoreCase(email)).toList();
    }

    private MvcResult postJson(String path, String token, String body) throws Exception {
        var request = post(path).header("Authorization", bearer(token));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request).andReturn();
    }

    @Test
    void alCancelarElProveedorElClienteRecibeUnCorreoConElMotivo() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        UUID id = book(c, p.serviceId(), upcomingMonday(), "10:00", "11:00");

        postJson("/api/v1/bookings/" + id + "/provider-cancellation", p.token(), "{\"reason\":\"Se dañó la silla\"}");

        List<EmailMessage> mails = mailsTo(c.email());
        assertThat(mails).hasSize(1);
        assertThat(mails.get(0).subject()).contains("fue cancelada");
        assertThat(mails.get(0).text()).contains("Corte", "10:00", "el negocio la canceló", "Motivo: Se dañó la silla");
    }

    @Test
    void alDesactivarElServicioConConfirmacionCadaClienteAfectadoRecibeSuCorreo() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup ana = newClient();
        ClientSetup luis = newClient();
        book(ana, p.serviceId(), upcomingMonday(), "09:00", "10:00");
        book(luis, p.serviceId(), upcomingMonday(), "11:00", "12:00");

        postJson("/api/v1/services/" + p.serviceId() + "/deactivation", p.token(), "{\"confirm\":true}");

        assertThat(mailsTo(ana.email())).singleElement().satisfies(m -> assertThat(m.text()).contains("el servicio dejó de estar disponible"));
        assertThat(mailsTo(luis.email())).hasSize(1);
    }

    @Test
    void alCerrarElRecursoConConfirmacionSoloAvisaALasReservasDelCierre() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup inside = newClient();
        ClientSetup outside = newClient();
        LocalDate monday = upcomingMonday();
        book(inside, p.serviceId(), monday, "10:00", "11:00");
        book(outside, p.serviceId(), monday, "15:00", "16:00");

        postJson("/api/v1/resources/" + p.resourceId() + "/closures", p.token(),
                "{\"start\":\"" + monday + " 09:00\",\"end\":\"" + monday + " 12:00\",\"confirm\":true}");

        assertThat(mailsTo(inside.email())).singleElement()
                .satisfies(m -> assertThat(m.text()).contains("no estará disponible en ese horario"));
        assertThat(mailsAfterWait(outside.email())).isEmpty();
    }

    @Test
    void siLaOperacionNoSeConfirmaNoSaleNingunCorreo() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        book(c, p.serviceId(), upcomingMonday(), "10:00", "11:00");

        MvcResult result = postJson("/api/v1/services/" + p.serviceId() + "/deactivation", p.token(), "{\"confirm\":false}");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(mailsAfterWait(c.email())).isEmpty();
    }

    @Test
    void cuandoElPropioClienteCancelaNoSeLeEscribe() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        UUID id = book(c, p.serviceId(), upcomingMonday(), "10:00", "11:00");

        postJson("/api/v1/bookings/" + id + "/cancellation", c.token(), null);

        assertThat(mailsAfterWait(c.email())).isEmpty();
    }

    @Test
    void alEliminarAlProveedorSusClientesRecibenElAviso() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin"));
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        book(c, p.serviceId(), upcomingMonday(), "10:00", "11:00");
        UUID providerUserId = userRepository.findByEmailIgnoreCase(p.email()).orElseThrow().getId();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/users/" + providerUserId)
                .header("Authorization", bearer(admin.token())).header("X-MFA-Code", admin.code())).andReturn();

        assertThat(mailsTo(c.email())).singleElement()
                .satisfies(m -> assertThat(m.text()).contains("el negocio ya no está en la plataforma"));
    }
}
