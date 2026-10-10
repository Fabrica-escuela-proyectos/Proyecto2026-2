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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-25 - Cancelar reserva como cliente (CP-HU25-01..06) contra Spring completo y PostgreSQL real. */
class BookingCancellationIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private BookingRepository bookingRepository;

    private record Provider(String token, UUID businessId, UUID serviceId) {
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
        return new Provider(token, businessId, serviceId);
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
        var request = post("/api/v1/bookings/" + bookingId + "/cancellation");
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

    /** Reserva insertada directo en la base (la API no deja reservar con menos de la antelación mínima). */
    private UUID rawBooking(Client c, Instant start, BookingStatus status) {
        return bookingRepository.saveAndFlush(Booking.builder().clientId(c.id()).clientEmail(c.email()).clientName("Cliente")
                .businessName("N").serviceName("S").resourceName("R").startAt(start).endAt(start.plusSeconds(3600))
                .status(status).priceCop(1L).build()).getId();
    }

    @Test
    void cancelarUnaReservaLaDejaCanceladaConOrigenMotivoYAuditoria() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID id = book(c, p, "10:00", "11:00");

        MvcResult result = cancel(c.token(), id, "{\"reason\":\"No puedo asistir\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.get("id").asText()).isEqualTo(id.toString());
        assertThat(body.get("status").asText()).isEqualTo("CANCELADA");
        assertThat(body.get("cancelOrigin").asText()).isEqualTo("CLIENTE");
        assertThat(body.get("cancelReason").asText()).isEqualTo("No puedo asistir");
        assertThat(body.get("cancelledAt").isNull()).isFalse();

        Booking saved = bookingRepository.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CANCELADA);
        assertThat(saved.getCancelOrigin()).isEqualTo(CancelOrigin.CLIENTE);
        assertThat(auditEvents(AuditEventType.CANCELACION_RESERVA, c.email())).hasSize(1);
    }

    @Test
    void elMotivoEsOpcionalYElCuerpoPuedeIrVacio() throws Exception {
        Provider p = provider();
        Client c = client();

        MvcResult withoutBody = cancel(c.token(), book(c, p, "09:00", "10:00"), null);
        MvcResult emptyObject = cancel(c.token(), book(c, p, "10:00", "11:00"), "{}");

        assertThat(withoutBody.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(withoutBody).get("cancelReason").isNull()).isTrue();
        assertThat(emptyObject.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unMotivoDemasiadoLargoDa400YNoCancela() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID id = book(c, p, "10:00", "11:00");

        MvcResult result = cancel(c.token(), id, "{\"reason\":\"" + "x".repeat(501) + "\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(bookingRepository.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
    }

    @Test
    void alCancelarElHorarioVuelveAEstarLibreYOtroClientePuedeReservarlo() throws Exception {
        Provider p = provider();
        Client first = client();
        UUID id = book(first, p, "10:00", "11:00");
        assertThat(freeSlots(p)).doesNotContain("10:00");

        cancel(first.token(), id, null);

        assertThat(freeSlots(p)).contains("10:00");
        assertThat(book(client(), p, "10:00", "11:00")).isNotNull();
    }

    @Test
    void laReservaCanceladaApareceCanceladaParaElClienteYElProveedor() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID id = book(c, p, "10:00", "11:00");
        cancel(c.token(), id, "{\"reason\":\"Cambio de planes\"}");

        JsonNode mine = json(mockMvc.perform(get("/api/v1/bookings/me").header("Authorization", "Bearer " + c.token()))
                .andReturn()).get("items").get(0);
        JsonNode business = json(mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/bookings")
                .header("Authorization", "Bearer " + p.token())).andReturn()).get("items").get(0);

        assertThat(mine.get("status").asText()).isEqualTo("CANCELADA");
        assertThat(mine.get("cancelReason").asText()).isEqualTo("Cambio de planes");
        assertThat(business.get("status").asText()).isEqualTo("CANCELADA");
        assertThat(business.get("cancelOrigin").asText()).isEqualTo("CLIENTE");
    }

    @Test
    void cancelarDosVecesDa409() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID id = book(c, p, "10:00", "11:00");
        cancel(c.token(), id, null);

        MvcResult second = cancel(c.token(), id, null);

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(second.getResponse().getContentAsString()).contains("ya está cancelada");
    }

    @Test
    void conMenosDeUnaHoraDeAntelacionDa409YNoCancela() throws Exception {
        Client c = client();
        UUID soon = rawBooking(c, Instant.now().plus(Duration.ofMinutes(30)), BookingStatus.CONFIRMADA);
        UUID started = rawBooking(c, Instant.now().minus(Duration.ofMinutes(10)), BookingStatus.CONFIRMADA);
        UUID later = rawBooking(c, Instant.now().plus(Duration.ofHours(3)), BookingStatus.CONFIRMADA);

        MvcResult tooLate = cancel(c.token(), soon, null);
        assertThat(tooLate.getResponse().getStatus()).isEqualTo(409);
        assertThat(tooLate.getResponse().getContentAsString()).contains("1 hora de antelación");
        assertThat(cancel(c.token(), started, null).getResponse().getStatus()).isEqualTo(409);
        assertThat(bookingRepository.findById(soon).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
        assertThat(cancel(c.token(), later, null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unaReservaCompletadaNoSePuedeCancelar() throws Exception {
        Client c = client();
        UUID done = rawBooking(c, Instant.now().plus(Duration.ofHours(5)), BookingStatus.COMPLETADA);

        assertThat(cancel(c.token(), done, null).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void laReservaDeOtroClienteDa403YNoCambia() throws Exception {
        Provider p = provider();
        Client owner = client();
        Client other = client();
        UUID id = book(owner, p, "10:00", "11:00");

        assertThat(cancel(other.token(), id, null).getResponse().getStatus()).isEqualTo(403);
        assertThat(bookingRepository.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
    }

    @Test
    void unProveedorNoPuedeCancelarComoClienteYSinSesionDa401() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID id = book(c, p, "10:00", "11:00");

        assertThat(cancel(p.token(), id, null).getResponse().getStatus()).isEqualTo(403);
        assertThat(cancel(null, id, null).getResponse().getStatus()).isEqualTo(401);
        assertThat(bookingRepository.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
    }

    @Test
    void unaReservaInexistenteDa404YUnIdMalFormadoDa400() throws Exception {
        Client c = client();

        assertThat(cancel(c.token(), UUID.randomUUID(), null).getResponse().getStatus()).isEqualTo(404);
        assertThat(cancel(c.token(), "no-es-uuid", null).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void dosCancelacionesSimultaneasDeLaMismaReservaDan200Y409() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID id = book(c, p, "10:00", "11:00");
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Integer> task = () -> {
                ready.countDown();
                go.await();
                return cancel(c.token(), id, null).getResponse().getStatus();
            };
            futures.add(pool.submit(task));
        }
        ready.await();
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(auditEvents(AuditEventType.CANCELACION_RESERVA, c.email())).hasSize(1);
    }
}
