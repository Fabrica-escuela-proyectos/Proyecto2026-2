package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.RepeatedTest;
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

/** HU-16 (desactivar recurso) y HU-17 (reactivar recurso) contra Spring completo y PostgreSQL real. */
class ResourceLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private ResourceRepository resourceRepository;

    private record Provider(String email, String token, UUID businessId, UUID serviceId, UUID resourceId) {
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
        UUID resourceId = addResource(token, businessId, serviceId, "Sala 1", List.of());
        return new Provider(email, token, businessId, serviceId, resourceId);
    }

    /** Crea un recurso con horario lunes-viernes 08:00-20:00 y lo asigna al servicio junto a los ya asignados. */
    private UUID addResource(String token, UUID businessId, UUID serviceId, String name, List<UUID> alreadyAssigned) throws Exception {
        UUID resourceId = UUID.fromString(json(mockMvc.perform(post("/api/v1/businesses/" + businessId + "/resources")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"type\":\"SALA\"}")).andExpect(status().isCreated()).andReturn()).get("id").asText());
        List<String> ids = new ArrayList<>(alreadyAssigned.stream().map(u -> "\"" + u + "\"").toList());
        ids.add("\"" + resourceId + "\"");
        mockMvc.perform(put("/api/v1/services/" + serviceId + "/resources").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"resourceIds\":[" + String.join(",", ids) + "]}")).andExpect(status().isOk());
        String week = "{\"days\":[" + String.join(",", IntStream.rangeClosed(1, 5).mapToObj(
                d -> "{\"dayOfWeek\":" + d + ",\"ranges\":[{\"start\":\"08:00\",\"end\":\"20:00\"}]}").toList()) + "]}";
        mockMvc.perform(put("/api/v1/resources/" + resourceId + "/availability").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(week)).andExpect(status().isOk());
        return resourceId;
    }

    private Client client() throws Exception {
        String email = uniqueEmail("cliente");
        registerClient(email, uniquePhone());
        return new Client(email, login(email), userRepository.findByEmailIgnoreCase(email).orElseThrow().getId());
    }

    private static LocalDate monday() {
        return LocalDate.now(BOGOTA).plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    private MvcResult bookResult(Client c, Provider p, String start, String end, UUID resourceId) throws Exception {
        String resource = resourceId == null ? "" : ",\"resourceId\":\"" + resourceId + "\"";
        return mockMvc.perform(post("/api/v1/bookings").header("Authorization", "Bearer " + c.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":\"" + p.serviceId() + "\",\"date\":\"" + monday() + "\",\"startTime\":\"" + start
                        + "\",\"endTime\":\"" + end + "\"" + resource + "}")).andReturn();
    }

    private UUID book(Client c, Provider p, String start, String end, UUID resourceId) throws Exception {
        MvcResult r = bookResult(c, p, start, end, resourceId);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return UUID.fromString(json(r).get("id").asText());
    }

    private MvcResult deactivate(String token, Object resourceId, String body) throws Exception {
        var request = post("/api/v1/resources/" + resourceId + "/deactivation");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult reactivate(String token, Object resourceId) throws Exception {
        var request = post("/api/v1/resources/" + resourceId + "/reactivation");
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

    private boolean active(UUID resourceId) {
        return resourceRepository.findById(resourceId).map(Resource::isActive).orElseThrow();
    }

    private BookingStatus statusOf(UUID bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow().getStatus();
    }

    // ---------------------------------------------------------------- HU-16

    @Test
    void sinReservasFuturasSeDesactivaDirectoYDejaDeOfrecerHorarios() throws Exception {
        Provider p = provider();
        assertThat(freeSlots(p)).isNotEmpty();

        MvcResult result = deactivate(p.token(), p.resourceId(), null);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("active").asBoolean()).isFalse();
        assertThat(json(result).get("cancelledBookings").asInt()).isZero();
        assertThat(active(p.resourceId())).isFalse();
        assertThat(freeSlots(p)).isEmpty();
        assertThat(bookResult(client(), p, "10:00", "11:00", null).getResponse().getStatus()).isEqualTo(409);
        assertThat(auditEvents(AuditEventType.DESACTIVACION_RECURSO, p.email())).hasSize(1);
    }

    @Test
    void conReservasFuturasSinConfirmarPideConfirmacionConLaCantidadYNoCambiaNada() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID a = book(c, p, "09:00", "10:00", null);
        UUID b = book(c, p, "10:00", "11:00", null);

        for (String body : new String[]{null, "{}", "{\"confirm\":false}"}) {
            MvcResult result = deactivate(p.token(), p.resourceId(), body);
            assertThat(result.getResponse().getStatus()).as(String.valueOf(body)).isEqualTo(409);
            assertThat(json(result).get("error").asText()).isEqualTo("CONFIRMATION_REQUIRED");
            assertThat(json(result).get("fields").get("affectedBookings").asText()).isEqualTo("2");
            assertThat(json(result).get("message").asText()).contains("2 reserva");
        }
        assertThat(active(p.resourceId())).isTrue();
        assertThat(statusOf(a)).isEqualTo(BookingStatus.CONFIRMADA);
        assertThat(statusOf(b)).isEqualTo(BookingStatus.CONFIRMADA);
    }

    @Test
    void alConfirmarSeDesactivaYSeCancelanLasReservasFuturasConOrigenRecursoNoDisponible() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID a = book(c, p, "09:00", "10:00", null);
        UUID b = book(client(), p, "10:00", "11:00", null);

        MvcResult result = deactivate(p.token(), p.resourceId(), "{\"confirm\":true}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("cancelledBookings").asInt()).isEqualTo(2);
        assertThat(active(p.resourceId())).isFalse();
        Booking saved = bookingRepository.findById(a).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CANCELADA);
        assertThat(saved.getCancelOrigin()).isEqualTo(CancelOrigin.RECURSO_NO_DISPONIBLE);
        assertThat(saved.getCancelReason()).contains("desactivado");
        assertThat(saved.getCancelledAt()).isNotNull();
        assertThat(statusOf(b)).isEqualTo(BookingStatus.CANCELADA);
        // El cliente ve la cancelación y su motivo.
        JsonNode mine = json(mockMvc.perform(get("/api/v1/bookings/me").header("Authorization", "Bearer " + c.token()))
                .andReturn()).get("items").get(0);
        assertThat(mine.get("status").asText()).isEqualTo("CANCELADA");
        assertThat(mine.get("cancelOrigin").asText()).isEqualTo("RECURSO_NO_DISPONIBLE");
    }

    @Test
    void laCancelacionPorDesactivacionNoAplicaLaReglaDeUnaHora() throws Exception {
        Provider p = provider();
        Client c = client();
        UUID soon = rawBooking(p, c, p.resourceId(), Instant.now().plus(Duration.ofMinutes(15)), BookingStatus.CONFIRMADA);

        MvcResult result = deactivate(p.token(), p.resourceId(), "{\"confirm\":true}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("cancelledBookings").asInt()).isEqualTo(1);
        assertThat(statusOf(soon)).isEqualTo(BookingStatus.CANCELADA);
    }

    @Test
    void elHistorialSeConservaYLasReservasDeOtrosRecursosNoSeTocan() throws Exception {
        Provider p = provider();
        UUID second = addResource(p.token(), p.businessId(), p.serviceId(), "Sala 2", List.of(p.resourceId()));
        Client c = client();
        UUID future = book(c, p, "10:00", "11:00", p.resourceId());
        UUID otherResource = book(c, p, "10:00", "11:00", second);
        UUID past = rawBooking(p, c, p.resourceId(), Instant.now().minus(Duration.ofDays(3)), BookingStatus.CONFIRMADA);
        UUID done = rawBooking(p, c, p.resourceId(), Instant.now().plus(Duration.ofDays(2)), BookingStatus.COMPLETADA);
        UUID alreadyCancelled = rawBooking(p, c, p.resourceId(), Instant.now().plus(Duration.ofDays(3)), BookingStatus.CANCELADA);

        MvcResult result = deactivate(p.token(), p.resourceId(), "{\"confirm\":true}");

        assertThat(json(result).get("cancelledBookings").asInt()).isEqualTo(1);
        assertThat(statusOf(future)).isEqualTo(BookingStatus.CANCELADA);
        assertThat(statusOf(otherResource)).isEqualTo(BookingStatus.CONFIRMADA);
        assertThat(statusOf(past)).isEqualTo(BookingStatus.CONFIRMADA);
        assertThat(statusOf(done)).isEqualTo(BookingStatus.COMPLETADA);
        assertThat(statusOf(alreadyCancelled)).isEqualTo(BookingStatus.CANCELADA);
        assertThat(bookingRepository.findById(alreadyCancelled).orElseThrow().getCancelOrigin()).isNull();
        assertThat(active(second)).isTrue();
    }

    @Test
    void desactivarUnRecursoYaInactivoEsIdempotente() throws Exception {
        Provider p = provider();
        deactivate(p.token(), p.resourceId(), null);

        MvcResult again = deactivate(p.token(), p.resourceId(), "{\"confirm\":true}");

        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(again).get("cancelledBookings").asInt()).isZero();
        assertThat(auditEvents(AuditEventType.DESACTIVACION_RECURSO, p.email())).hasSize(1);
    }

    @Test
    void unRecursoAjenoDa403YNoCambiaNada() throws Exception {
        Provider owner = provider();
        Provider other = provider();
        UUID booking = book(client(), owner, "10:00", "11:00", null);

        assertThat(deactivate(other.token(), owner.resourceId(), "{\"confirm\":true}").getResponse().getStatus()).isEqualTo(403);
        assertThat(active(owner.resourceId())).isTrue();
        assertThat(statusOf(booking)).isEqualTo(BookingStatus.CONFIRMADA);
    }

    @Test
    void unClienteSinSesionOInexistenteSeRechazan() throws Exception {
        Provider p = provider();
        Client c = client();

        assertThat(deactivate(c.token(), p.resourceId(), null).getResponse().getStatus()).isEqualTo(403);
        assertThat(deactivate(null, p.resourceId(), null).getResponse().getStatus()).isEqualTo(401);
        assertThat(deactivate(p.token(), UUID.randomUUID(), null).getResponse().getStatus()).isEqualTo(404);
        assertThat(deactivate(p.token(), "no-es-uuid", null).getResponse().getStatus()).isEqualTo(400);
        assertThat(active(p.resourceId())).isTrue();
    }

    /** Se repite porque el resultado depende del orden en que llegan las dos peticiones (así se detectó un fallo real). */
    @RepeatedTest(8)
    void unaReservaYUnaDesactivacionSimultaneasNuncaDejanUnaReservaConfirmadaEnUnRecursoInactivo() throws Exception {
        Provider p = provider();
        Client c = client();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> booking = () -> {
            ready.countDown();
            go.await();
            return bookResult(c, p, "10:00", "11:00", null).getResponse().getStatus();
        };
        Callable<Integer> deactivation = () -> {
            ready.countDown();
            go.await();
            return deactivate(p.token(), p.resourceId(), "{\"confirm\":true}").getResponse().getStatus();
        };
        Future<Integer> b = pool.submit(booking);
        Future<Integer> d = pool.submit(deactivation);
        ready.await();
        go.countDown();
        int bookingStatus = b.get();
        int deactivationStatus = d.get();
        pool.shutdown();

        // Cualquier orden es válido: o la reserva entró antes y la desactivación la canceló, o llegó tarde y se rechazó.
        assertThat(deactivationStatus).isEqualTo(200);
        assertThat(bookingStatus).isIn(201, 409);
        assertThat(active(p.resourceId())).isFalse();
        long confirmedOnInactive = bookingRepository.findAll().stream()
                .filter(x -> p.resourceId().equals(x.getResourceId()) && x.getStatus() == BookingStatus.CONFIRMADA).count();
        assertThat(confirmedOnInactive).isZero();
    }

    // ---------------------------------------------------------------- HU-17

    @Test
    void reactivarVuelveAActivarElMismoRecursoSinDuplicarloYVuelveAOfrecerHorarios() throws Exception {
        Provider p = provider();
        deactivate(p.token(), p.resourceId(), null);
        assertThat(freeSlots(p)).isEmpty();
        long resourcesBefore = resourceRepository.findAll().stream().filter(r -> p.businessId().equals(r.getBusinessId())).count();

        MvcResult result = reactivate(p.token(), p.resourceId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("active").asBoolean()).isTrue();
        assertThat(json(result).get("resourceId").asText()).isEqualTo(p.resourceId().toString());
        assertThat(active(p.resourceId())).isTrue();
        assertThat(resourceRepository.findAll().stream().filter(r -> p.businessId().equals(r.getBusinessId())).count())
                .isEqualTo(resourcesBefore);
        assertThat(freeSlots(p)).isNotEmpty();
        assertThat(bookResult(client(), p, "10:00", "11:00", null).getResponse().getStatus()).isEqualTo(201);
        assertThat(auditEvents(AuditEventType.REACTIVACION_RECURSO, p.email())).hasSize(1);
    }

    @Test
    void lasReservasCanceladasPorLaDesactivacionSiguenCanceladasAlReactivar() throws Exception {
        Provider p = provider();
        UUID booking = book(client(), p, "10:00", "11:00", null);
        deactivate(p.token(), p.resourceId(), "{\"confirm\":true}");

        reactivate(p.token(), p.resourceId());

        assertThat(statusOf(booking)).isEqualTo(BookingStatus.CANCELADA);
        // Y el horario que liberó la cancelación vuelve a poder reservarse.
        assertThat(freeSlots(p)).contains("10:00");
    }

    @Test
    void reactivarUnRecursoYaActivoEsIdempotente() throws Exception {
        Provider p = provider();

        MvcResult result = reactivate(p.token(), p.resourceId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("active").asBoolean()).isTrue();
        assertThat(auditEvents(AuditEventType.REACTIVACION_RECURSO, p.email())).isEmpty();
    }

    @Test
    void reactivarUnRecursoAjenoOComoClienteOSinSesionSeRechaza() throws Exception {
        Provider owner = provider();
        Provider other = provider();
        deactivate(owner.token(), owner.resourceId(), null);

        assertThat(reactivate(other.token(), owner.resourceId()).getResponse().getStatus()).isEqualTo(403);
        assertThat(reactivate(client().token(), owner.resourceId()).getResponse().getStatus()).isEqualTo(403);
        assertThat(reactivate(null, owner.resourceId()).getResponse().getStatus()).isEqualTo(401);
        assertThat(reactivate(owner.token(), UUID.randomUUID()).getResponse().getStatus()).isEqualTo(404);
        assertThat(reactivate(owner.token(), "no-es-uuid").getResponse().getStatus()).isEqualTo(400);
        assertThat(active(owner.resourceId())).isFalse();
    }

    private UUID rawBooking(Provider p, Client c, UUID resourceId, Instant start, BookingStatus status) {
        return bookingRepository.saveAndFlush(Booking.builder().clientId(c.id()).clientEmail(c.email()).clientName("Cliente")
                .businessId(p.businessId()).businessName("N").serviceId(p.serviceId()).serviceName("S").resourceId(resourceId)
                .resourceName("R").startAt(start).endAt(start.plusSeconds(3600)).status(status).priceCop(1L).build()).getId();
    }
}
