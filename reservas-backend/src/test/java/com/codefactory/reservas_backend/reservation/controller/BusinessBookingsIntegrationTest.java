package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.identity.domain.User;
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

/** HU-24 - Consultar reservas del negocio (CP-HU24-01..06) contra Spring completo y PostgreSQL real. */
class BusinessBookingsIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private BookingRepository bookingRepository;

    private record Provider(String token, UUID businessId, UUID serviceId) {
    }

    private record Client(String email, String token) {
    }

    /** Proveedor con negocio, servicio de 60 min, un recurso y horario lunes a viernes 08:00-20:00. */
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
        return new Provider(token, businessId, serviceId);
    }

    private Client client() throws Exception {
        String email = uniqueEmail("cliente");
        registerClient(email, uniquePhone());
        return new Client(email, login(email));
    }

    private static LocalDate monday() {
        return LocalDate.now(BOGOTA).plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    private UUID book(Client c, Provider p, LocalDate date, String start, String end) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/bookings").header("Authorization", "Bearer " + c.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":\"" + p.serviceId() + "\",\"date\":\"" + date + "\",\"startTime\":\"" + start
                        + "\",\"endTime\":\"" + end + "\"}")).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json(r).get("id").asText());
    }

    private MvcResult list(String token, UUID businessId, String query) throws Exception {
        var request = get("/api/v1/businesses/" + businessId + "/bookings" + query);
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

    private void cancel(UUID bookingId) {
        Booking b = bookingRepository.findById(bookingId).orElseThrow();
        b.setStatus(BookingStatus.CANCELADA);
        b.setCancelReason("No puedo asistir");
        bookingRepository.save(b);
    }

    @Test
    void elProveedorVeLasReservasDeTodosSusClientesConFechaHorasServicioYNombreDelCliente() throws Exception {
        Provider p = provider();
        Client a = client();
        Client b = client();
        UUID fromA = book(a, p, monday(), "09:00", "10:00");
        UUID fromB = book(b, p, monday(), "10:00", "11:00");

        MvcResult result = list(p.token(), p.businessId(), "");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode page = json(result);
        assertThat(ids(page)).containsExactly(fromB.toString(), fromA.toString());
        JsonNode item = page.get("items").get(1);
        assertThat(item.get("clientName").asText()).isEqualTo("Usuario de Prueba");
        assertThat(item.get("date").asText()).isEqualTo(monday().toString());
        assertThat(item.get("startTime").asText()).isEqualTo("09:00");
        assertThat(item.get("endTime").asText()).isEqualTo("10:00");
        assertThat(item.get("serviceName").asText()).isEqualTo("Corte");
        assertThat(item.get("status").asText()).isEqualTo("CONFIRMADA");
        assertThat(page.get("message").isNull()).isTrue();
        // Trae los datos del cliente que la reserva conserva: id, nombre y correo (no el celular).
        assertThat(item.get("clientEmail").asText()).isEqualToIgnoringCase(a.email());
        assertThat(page.get("items").get(0).get("clientEmail").asText()).isEqualToIgnoringCase(b.email());
        assertThat(item.get("clientId").asText())
                .isEqualTo(userRepository.findByEmailIgnoreCase(a.email()).orElseThrow().getId().toString());
        assertThat(item.has("clientPhone")).isFalse();
        assertThat(item.has("cellphone")).isFalse();
    }

    @Test
    void unNegocioSinReservasDevuelveListaVaciaYMensaje() throws Exception {
        Provider p = provider();

        MvcResult result = list(p.token(), p.businessId(), "");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("items")).isEmpty();
        assertThat(json(result).get("message").asText()).contains("No hay reservas");
    }

    @Test
    void soloSeVenLasReservasDeSuNegocioNoLasDeOtros() throws Exception {
        Provider mine = provider();
        Provider other = provider();
        Client c = client();
        UUID inMine = book(c, mine, monday(), "09:00", "10:00");
        UUID inOther = book(c, other, monday(), "09:00", "10:00");

        assertThat(ids(json(list(mine.token(), mine.businessId(), "")))).containsExactly(inMine.toString());
        assertThat(ids(json(list(other.token(), other.businessId(), "")))).containsExactly(inOther.toString());
    }

    @Test
    void unProveedorNoPuedeVerLasReservasDeOtroNegocio() throws Exception {
        Provider mine = provider();
        Provider other = provider();
        book(client(), other, monday(), "09:00", "10:00");

        MvcResult result = list(mine.token(), other.businessId(), "");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Usuario de Prueba").doesNotContain("@example.com\"");
    }

    @Test
    void unClienteRecibe403YSinSesion401YUnNegocioInexistente404() throws Exception {
        Provider p = provider();
        Client c = client();

        assertThat(list(c.token(), p.businessId(), "").getResponse().getStatus()).isEqualTo(403);
        assertThat(list(null, p.businessId(), "").getResponse().getStatus()).isEqualTo(401);
        assertThat(list(p.token(), UUID.randomUUID(), "").getResponse().getStatus()).isEqualTo(404);
        mockMvc.perform(get("/api/v1/businesses/no-es-uuid/bookings").header("Authorization", "Bearer " + p.token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elFiltroPorEstadoSoloDevuelveEsasReservas() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID keep = book(c, p, monday(), "09:00", "10:00");
        UUID cancelled = book(c, p, monday(), "10:00", "11:00");
        cancel(cancelled);

        assertThat(ids(json(list(p.token(), p.businessId(), "?status=confirmada")))).containsExactly(keep.toString());
        MvcResult onlyCancelled = list(p.token(), p.businessId(), "?status=CANCELADA");
        assertThat(ids(json(onlyCancelled))).containsExactly(cancelled.toString());
        assertThat(json(onlyCancelled).get("items").get(0).get("cancelReason").asText()).isEqualTo("No puedo asistir");
        assertThat(list(p.token(), p.businessId(), "?status=PENDIENTE").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void losFiltrosDeFechaSonInclusivosPorDiaDeBogota() throws Exception {
        Provider p = provider();
        Client c = client();
        LocalDate d1 = monday();
        LocalDate d2 = monday().plusDays(1);
        LocalDate d3 = monday().plusDays(2);
        UUID r1 = book(c, p, d1, "09:00", "10:00");
        // 19:00-20:00 en Bogotá ya es el día siguiente en UTC: debe seguir contando para su día local.
        UUID r2 = book(c, p, d2, "19:00", "20:00");
        UUID r3 = book(c, p, d3, "08:00", "09:00");

        assertThat(ids(json(list(p.token(), p.businessId(), "?from=" + d2 + "&to=" + d2)))).containsExactly(r2.toString());
        assertThat(ids(json(list(p.token(), p.businessId(), "?from=" + d2)))).containsExactly(r3.toString(), r2.toString());
        assertThat(ids(json(list(p.token(), p.businessId(), "?to=" + d2)))).containsExactly(r2.toString(), r1.toString());
        assertThat(ids(json(list(p.token(), p.businessId(), "?from=" + d1 + "&to=" + d3)))).hasSize(3);

        MvcResult none = list(p.token(), p.businessId(), "?from=" + d3.plusDays(10));
        assertThat(json(none).get("items")).isEmpty();
        assertThat(json(none).get("message").asText()).contains("filtros");
    }

    @Test
    void fechasInvalidasORangoInvertidoDan400() throws Exception {
        Provider p = provider();

        assertThat(list(p.token(), p.businessId(), "?from=32/13/2026").getResponse().getStatus()).isEqualTo(400);
        assertThat(list(p.token(), p.businessId(), "?to=2026-99-99").getResponse().getStatus()).isEqualTo(400);
        MvcResult inverted = list(p.token(), p.businessId(), "?from=2026-10-20&to=2026-10-19");
        assertThat(inverted.getResponse().getStatus()).isEqualTo(400);
        assertThat(inverted.getResponse().getContentAsString()).contains("no puede ser posterior");
    }

    @Test
    void laPaginacionRespetaElTamanoYElOrden() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID first = book(c, p, monday(), "09:00", "10:00");
        UUID second = book(c, p, monday(), "10:00", "11:00");
        UUID third = book(c, p, monday(), "11:00", "12:00");

        JsonNode page0 = json(list(p.token(), p.businessId(), "?page=0&size=2"));
        JsonNode page1 = json(list(p.token(), p.businessId(), "?page=1&size=2"));

        assertThat(ids(page0)).containsExactly(third.toString(), second.toString());
        assertThat(ids(page1)).containsExactly(first.toString());
        assertThat(page0.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(json(list(p.token(), p.businessId(), "?size=100000")).get("size").asInt()).isEqualTo(50);
        assertThat(list(p.token(), p.businessId(), "?page=-1").getResponse().getStatus()).isEqualTo(400);
        assertThat(list(p.token(), p.businessId(), "?size=0").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void elNombreYElCorreoDelClienteSonLaCopiaDeCuandoReservoAunqueDespuesCambien() throws Exception {
        Provider p = provider();
        Client c = client();
        book(c, p, monday(), "09:00", "10:00");

        User user = userRepository.findByEmailIgnoreCase(c.email()).orElseThrow();
        user.setFullName("Nombre Nuevo");
        userRepository.save(user);

        JsonNode item = json(list(p.token(), p.businessId(), "")).get("items").get(0);
        assertThat(item.get("clientName").asText()).isEqualTo("Usuario de Prueba");
        assertThat(item.get("clientEmail").asText()).isEqualToIgnoringCase(c.email());
    }
}
