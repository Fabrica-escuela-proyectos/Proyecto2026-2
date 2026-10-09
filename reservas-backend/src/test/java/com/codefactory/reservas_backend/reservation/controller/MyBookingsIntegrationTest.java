package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-23 - Consultar mis reservas (CP-HU23-01..06) contra Spring completo y PostgreSQL real. */
class MyBookingsIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private BookingRepository bookingRepository;

    private record Client(String email, String token, UUID id) {
    }

    private record Fixture(String providerToken, UUID serviceId) {
    }

    private Client client() throws Exception {
        String email = uniqueEmail("cliente");
        registerClient(email, uniquePhone());
        return new Client(email, login(email), userRepository.findByEmailIgnoreCase(email).orElseThrow().getId());
    }

    /** Servicio de 60 min con un recurso y horario de lunes a viernes 08:00-20:00. */
    private Fixture fixture() throws Exception {
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
        String week = "{\"days\":[" + String.join(",", java.util.stream.IntStream.rangeClosed(1, 5).mapToObj(
                d -> "{\"dayOfWeek\":" + d + ",\"ranges\":[{\"start\":\"08:00\",\"end\":\"20:00\"}]}").toList()) + "]}";
        mockMvc.perform(put("/api/v1/resources/" + resourceId + "/availability").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(week)).andExpect(status().isOk());
        return new Fixture(token, serviceId);
    }

    private static LocalDate monday() {
        return LocalDate.now(BOGOTA).plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    private UUID book(Client c, Fixture f, LocalDate date, String start, String end) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/bookings").header("Authorization", "Bearer " + c.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":\"" + f.serviceId() + "\",\"date\":\"" + date + "\",\"startTime\":\"" + start
                        + "\",\"endTime\":\"" + end + "\"}")).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json(r).get("id").asText());
    }

    private MvcResult list(String token, String url) throws Exception {
        var request = get(url);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(i -> ids.add(i.get("id").asText()));
        return ids;
    }

    @Test
    void elClienteVeSusReservasConFechaHorasServicioYEstado() throws Exception {
        Fixture f = fixture();
        Client c = client();
        UUID id = book(c, f, monday(), "10:00", "11:00");

        MvcResult result = list(c.token(), "/api/v1/bookings/me");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode page = json(result);
        assertThat(page.get("items")).hasSize(1);
        JsonNode item = page.get("items").get(0);
        assertThat(item.get("id").asText()).isEqualTo(id.toString());
        assertThat(item.get("date").asText()).isEqualTo(monday().toString());
        assertThat(item.get("startTime").asText()).isEqualTo("10:00");
        assertThat(item.get("endTime").asText()).isEqualTo("11:00");
        assertThat(item.get("serviceName").asText()).isEqualTo("Corte");
        assertThat(item.get("status").asText()).isEqualTo("CONFIRMADA");
        assertThat(item.get("priceCop").asLong()).isEqualTo(25_000L);
        assertThat(item.get("cancelReason").isNull()).isTrue();
        assertThat(page.get("message").isNull()).isTrue();
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void unClienteSinReservasRecibeListaVaciaYUnMensaje() throws Exception {
        Client c = client();

        MvcResult result = list(c.token(), "/api/v1/bookings/me");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("items")).isEmpty();
        assertThat(json(result).get("message").asText()).contains("No tiene reservas");
    }

    @Test
    void cadaClienteVeSoloLasSuyas() throws Exception {
        Fixture f = fixture();
        Client a = client();
        Client b = client();
        UUID fromA = book(a, f, monday(), "09:00", "10:00");
        UUID fromB = book(b, f, monday(), "10:00", "11:00");

        assertThat(ids(json(list(a.token(), "/api/v1/bookings/me")))).containsExactly(fromA.toString());
        assertThat(ids(json(list(b.token(), "/api/v1/bookings/me")))).containsExactly(fromB.toString());
    }

    @Test
    void lasReservasVienenDeLaMasRecienteALaMasAntigua() throws Exception {
        Fixture f = fixture();
        Client c = client();
        UUID early = book(c, f, monday(), "09:00", "10:00");
        UUID late = book(c, f, monday().plusDays(1), "09:00", "10:00");
        UUID middle = book(c, f, monday(), "15:00", "16:00");

        List<String> ordered = ids(json(list(c.token(), "/api/v1/bookings/me")));

        assertThat(ordered).containsExactly(late.toString(), middle.toString(), early.toString());
    }

    @Test
    void unaReservaCanceladaMuestraSuMotivo() throws Exception {
        Fixture f = fixture();
        Client c = client();
        UUID id = book(c, f, monday(), "10:00", "11:00");
        Booking booking = bookingRepository.findById(id).orElseThrow();
        booking.setStatus(BookingStatus.CANCELADA);
        booking.setCancelReason("No puedo asistir");
        booking.setCancelledAt(Instant.now());
        bookingRepository.save(booking);

        JsonNode item = json(list(c.token(), "/api/v1/bookings/me")).get("items").get(0);

        assertThat(item.get("status").asText()).isEqualTo("CANCELADA");
        assertThat(item.get("cancelReason").asText()).isEqualTo("No puedo asistir");
        assertThat(item.get("cancelledAt").isNull()).isFalse();
    }

    @Test
    void elFiltroPorEstadoSoloDevuelveEsasReservas() throws Exception {
        Fixture f = fixture();
        Client c = client();
        UUID keep = book(c, f, monday(), "09:00", "10:00");
        UUID cancel = book(c, f, monday(), "10:00", "11:00");
        Booking booking = bookingRepository.findById(cancel).orElseThrow();
        booking.setStatus(BookingStatus.CANCELADA);
        bookingRepository.save(booking);

        assertThat(ids(json(list(c.token(), "/api/v1/bookings/me?status=confirmada")))).containsExactly(keep.toString());
        assertThat(ids(json(list(c.token(), "/api/v1/bookings/me?status=CANCELADA")))).containsExactly(cancel.toString());
        MvcResult none = list(c.token(), "/api/v1/bookings/me?status=COMPLETADA");
        assertThat(json(none).get("items")).isEmpty();
        assertThat(json(none).get("message").asText()).contains("estado indicado");
        assertThat(list(c.token(), "/api/v1/bookings/me?status=PENDIENTE").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void laPaginacionRespetaElTamanoYElOrden() throws Exception {
        Fixture f = fixture();
        Client c = client();
        UUID first = book(c, f, monday(), "09:00", "10:00");
        UUID second = book(c, f, monday(), "10:00", "11:00");
        UUID third = book(c, f, monday(), "11:00", "12:00");

        JsonNode page0 = json(list(c.token(), "/api/v1/bookings/me?page=0&size=2"));
        JsonNode page1 = json(list(c.token(), "/api/v1/bookings/me?page=1&size=2"));

        assertThat(ids(page0)).containsExactly(third.toString(), second.toString());
        assertThat(ids(page1)).containsExactly(first.toString());
        assertThat(page0.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(json(list(c.token(), "/api/v1/bookings/me?size=100000")).get("size").asInt()).isEqualTo(50);
    }

    @Test
    void parametrosInvalidosDevuelven400() throws Exception {
        Client c = client();

        assertThat(list(c.token(), "/api/v1/bookings/me?page=-1").getResponse().getStatus()).isEqualTo(400);
        assertThat(list(c.token(), "/api/v1/bookings/me?size=0").getResponse().getStatus()).isEqualTo(400);
        assertThat(list(c.token(), "/api/v1/bookings/me?size=abc").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void laRutaPorUsuarioDevuelveLoMismoSiEsElPropioY403SiEsDeOtro() throws Exception {
        Fixture f = fixture();
        Client a = client();
        Client b = client();
        UUID fromA = book(a, f, monday(), "09:00", "10:00");
        book(b, f, monday(), "10:00", "11:00");

        assertThat(ids(json(list(a.token(), "/api/v1/users/" + a.id() + "/bookings")))).containsExactly(fromA.toString());

        MvcResult other = list(a.token(), "/api/v1/users/" + b.id() + "/bookings");
        assertThat(other.getResponse().getStatus()).isEqualTo(403);
        assertThat(other.getResponse().getContentAsString()).doesNotContain(b.email());
        // Un id inexistente da el mismo 403: no se revela si el usuario existe.
        assertThat(list(a.token(), "/api/v1/users/" + UUID.randomUUID() + "/bookings").getResponse().getStatus()).isEqualTo(403);
        assertThat(list(a.token(), "/api/v1/users/no-es-uuid/bookings").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void sinSesionDevuelve401() throws Exception {
        Client c = client();

        assertThat(list(null, "/api/v1/bookings/me").getResponse().getStatus()).isEqualTo(401);
        assertThat(list(null, "/api/v1/users/" + c.id() + "/bookings").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void unProveedorNoTieneReservasDeClienteYRecibe403() throws Exception {
        Fixture f = fixture();

        assertThat(list(f.providerToken(), "/api/v1/bookings/me").getResponse().getStatus()).isEqualTo(403);
    }
}
