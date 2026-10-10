package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-26 - Cancelar reserva como proveedor (CP-HU26-01..06) contra Spring completo y PostgreSQL real. */
class ProviderCancellationIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private BookingRepository bookingRepository;

    private record Provider(String email, String token, UUID businessId, UUID serviceId) {
    }

    private record Client(String email, String token, UUID id) {
    }

    private Provider provider() throws Exception {
        String email = uniqueEmail("proveedor");
        String payload = """
                {"fullName":"Proveedor de Prueba","email":"%s","cellphone":"%s","password":"%s","businessName":"Negocio %s"}
                """.formatted(email, uniquePhone(), PASSWORD, UUID.randomUUID().toString().substring(0, 6));
        MvcResult reg = mockMvc.perform(post("/api/v1/providers").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn();
        UUID businessId = UUID.fromString(json(reg).get("businessId").asText());
        String token = login(email);
        UUID serviceId = UUID.fromString(json(mockMvc.perform(post("/api/v1/businesses/" + businessId + "/services")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Corte\",\"durationMinutes\":60,\"priceCop\":25000}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asText());
        UUID resourceId = UUID.fromString(json(mockMvc.perform(post("/api/v1/businesses/" + businessId + "/resources")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sala 1\",\"type\":\"SALA\"}")).andExpect(status().isCreated()).andReturn()).get("id").asText());
        mockMvc.perform(put("/api/v1/services/" + serviceId + "/resources").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"resourceIds\":[\"" + resourceId + "\"]}")).andExpect(status().isOk());
        String week = "{\"days\":[" + String.join(",", IntStream.rangeClosed(1, 5).mapToObj(
                d -> "{\"dayOfWeek\":" + d + ",\"ranges\":[{\"start\":\"08:00\",\"end\":\"20:00\"}]}").toList()) + "]}";
        mockMvc.perform(put("/api/v1/resources/" + resourceId + "/availability").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(week)).andExpect(status().isOk());
        return new Provider(email, token, businessId, serviceId);
    }

    private Client client() throws Exception {
        String email = uniqueEmail("cliente");
        registerClient(email, uniquePhone());
        return new Client(email, login(email), userRepository.findByEmailIgnoreCase(email).orElseThrow().getId());
    }

    private static LocalDate monday() {
        return LocalDate.now(BOGOTA).plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    private UUID book(Client c, Provider p, String start, String end) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/bookings").header("Authorization", "Bearer " + c.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":\"" + p.serviceId() + "\",\"date\":\"" + monday() + "\",\"startTime\":\"" + start
                        + "\",\"endTime\":\"" + end + "\"}")).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json(r).get("id").asText());
    }

    private MvcResult cancel(String token, Object bookingId, String body) throws Exception {
        var request = post("/api/v1/bookings/" + bookingId + "/provider-cancellation");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private List<String> freeSlots(Provider p) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/services/" + p.serviceId() + "/availability?date=" + monday()))
                .andExpect(status().isOk()).andReturn();
        List<String> starts = new ArrayList<>();
        json(r).get("slots").forEach(s -> starts.add(s.get("start").asText()));
        return starts;
    }

    @Test
    void elProveedorCancelaConMotivoYLaReservaQuedaCanceladaConOrigenProveedor() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID id = book(c, p, "10:00", "11:00");

        MvcResult result = cancel(p.token(), id, "{\"reason\":\"El recurso está en mantenimiento\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.get("status").asText()).isEqualTo("CANCELADA");
        assertThat(body.get("cancelOrigin").asText()).isEqualTo("PROVEEDOR");
        assertThat(body.get("cancelReason").asText()).isEqualTo("El recurso está en mantenimiento");
        Booking saved = bookingRepository.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CANCELADA);
        assertThat(saved.getCancelOrigin()).isEqualTo(CancelOrigin.PROVEEDOR);
        assertThat(auditEvents(AuditEventType.CANCELACION_RESERVA, p.email())).hasSize(1);
    }

    @Test
    void elClienteVeElMotivoEnSusReservas() throws Exception {
        Provider p = provider();
        Client c = client();
        book(c, p, "10:00", "11:00");
        UUID id = UUID.fromString(json(mockMvc.perform(get("/api/v1/bookings/me")
                .header("Authorization", "Bearer " + c.token())).andReturn()).get("items").get(0).get("id").asText());

        cancel(p.token(), id, "{\"reason\":\"Cierre por imprevisto\"}");

        JsonNode item = json(mockMvc.perform(get("/api/v1/bookings/me").header("Authorization", "Bearer " + c.token()))
                .andReturn()).get("items").get(0);
        assertThat(item.get("status").asText()).isEqualTo("CANCELADA");
        assertThat(item.get("cancelOrigin").asText()).isEqualTo("PROVEEDOR");
        assertThat(item.get("cancelReason").asText()).isEqualTo("Cierre por imprevisto");
    }

    @Test
    void elHorarioQuedaLibreOtraVez() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID id = book(c, p, "10:00", "11:00");
        assertThat(freeSlots(p)).doesNotContain("10:00");

        cancel(p.token(), id, "{\"reason\":\"Imprevisto\"}");

        assertThat(freeSlots(p)).contains("10:00");
        assertThat(book(client(), p, "10:00", "11:00")).isNotNull();
    }

    @Test
    void sinMotivoOMotivoVacioODemasiadoLargoDa400YNoCancela() throws Exception {
        Provider p = provider();
        UUID id = book(client(), p, "10:00", "11:00");

        for (String body : new String[]{"{}", "{\"reason\":\"\"}", "{\"reason\":\"   \"}", "{\"reason\":null}",
                "{\"reason\":\"" + "x".repeat(501) + "\"}", "", "no-es-json"}) {
            assertThat(cancel(p.token(), id, body.isEmpty() ? null : body).getResponse().getStatus()).as(body).isEqualTo(400);
        }
        assertThat(bookingRepository.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
    }

    @Test
    void sePuedeCancelarFaltandoMenosDeUnaHoraPeroNoUnaReservaYaIniciada() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID soon = rawBooking(p, c, Instant.now().plus(Duration.ofMinutes(20)), BookingStatus.CONFIRMADA);
        UUID started = rawBooking(p, c, Instant.now().minus(Duration.ofMinutes(20)), BookingStatus.CONFIRMADA);

        assertThat(cancel(p.token(), soon, "{\"reason\":\"Imprevisto\"}").getResponse().getStatus()).isEqualTo(200);
        MvcResult tooLate = cancel(p.token(), started, "{\"reason\":\"Imprevisto\"}");
        assertThat(tooLate.getResponse().getStatus()).isEqualTo(409);
        assertThat(tooLate.getResponse().getContentAsString()).contains("ya inició");
    }

    @Test
    void cancelarDosVecesOUnaCompletadaDa409() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID id = book(c, p, "10:00", "11:00");
        UUID done = rawBooking(p, c, Instant.now().plus(Duration.ofHours(5)), BookingStatus.COMPLETADA);
        cancel(p.token(), id, "{\"reason\":\"Imprevisto\"}");

        assertThat(cancel(p.token(), id, "{\"reason\":\"Otra vez\"}").getResponse().getStatus()).isEqualTo(409);
        assertThat(cancel(p.token(), done, "{\"reason\":\"Imprevisto\"}").getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void unProveedorNoPuedeCancelarReservasDeOtroNegocio() throws Exception {
        Provider owner = provider();
        Provider other = provider();
        UUID id = book(client(), owner, "10:00", "11:00");

        assertThat(cancel(other.token(), id, "{\"reason\":\"Intruso\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(bookingRepository.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
    }

    @Test
    void unClienteNoPuedeUsarLaRutaDelProveedorNiSinSesion() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID id = book(c, p, "10:00", "11:00");

        assertThat(cancel(c.token(), id, "{\"reason\":\"Yo mismo\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(cancel(null, id, "{\"reason\":\"Anónimo\"}").getResponse().getStatus()).isEqualTo(401);
        assertThat(bookingRepository.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
    }

    @Test
    void unaReservaInexistenteDa404YUnIdMalFormadoDa400() throws Exception {
        Provider p = provider();

        assertThat(cancel(p.token(), UUID.randomUUID(), "{\"reason\":\"x\"}").getResponse().getStatus()).isEqualTo(404);
        assertThat(cancel(p.token(), "no-es-uuid", "{\"reason\":\"x\"}").getResponse().getStatus()).isEqualTo(400);
    }

    private UUID rawBooking(Provider p, Client c, Instant start, BookingStatus status) {
        return bookingRepository.saveAndFlush(Booking.builder().clientId(c.id()).clientEmail(c.email()).clientName("Cliente")
                .businessId(p.businessId()).businessName("N").serviceName("S").resourceName("R").startAt(start)
                .endAt(start.plusSeconds(3600)).status(status).priceCop(1L).build()).getId();
    }
}
