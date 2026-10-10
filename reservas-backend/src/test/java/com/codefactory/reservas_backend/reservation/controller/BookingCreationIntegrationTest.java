package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-22 - Crear reserva (CP-HU22-01..11) contra Spring completo y PostgreSQL real: incluye la
 * restricción EXCLUDE anti-overbooking, la concurrencia y el cruce con la disponibilidad de HU-20.
 */
class BookingCreationIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private ServiceOfferingRepository serviceRepository;

    private record Fixture(String token, UUID businessId, UUID serviceId, UUID resourceId) {
    }

    private static LocalDate monday() {
        return LocalDate.now(BOGOTA).plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    /** Proveedor con negocio, servicio de 60 min a 25.000 COP, un recurso asignado y lunes 09:00-12:00 + 14:00-16:00. */
    private Fixture fixture() throws Exception {
        String email = uniqueEmail("proveedor");
        String payload = """
                {"fullName":"Proveedor de Prueba","email":"%s","cellphone":"%s","password":"%s","businessName":"Negocio %s"}
                """.formatted(email, uniquePhone(), PASSWORD, UUID.randomUUID().toString().substring(0, 6));
        MvcResult reg = mockMvc.perform(post("/api/v1/providers").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn();
        UUID businessId = UUID.fromString(json(reg).get("businessId").asText());
        String token = login(email);
        UUID serviceId = createService(token, businessId, "Corte");
        UUID resourceId = createResource(token, businessId, "Sala 1");
        assign(token, serviceId, resourceId);
        setSchedule(token, resourceId);
        return new Fixture(token, businessId, serviceId, resourceId);
    }

    private UUID createService(String token, UUID businessId, String name) throws Exception {
        MvcResult s = mockMvc.perform(post("/api/v1/businesses/" + businessId + "/services")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"durationMinutes\":60,\"priceCop\":25000}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json(s).get("id").asText());
    }

    private UUID createResource(String token, UUID businessId, String name) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/businesses/" + businessId + "/resources")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"SALA\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json(r).get("id").asText());
    }

    private void assign(String token, UUID serviceId, UUID... resourceIds) throws Exception {
        List<String> quoted = new ArrayList<>();
        for (UUID id : resourceIds) {
            quoted.add("\"" + id + "\"");
        }
        mockMvc.perform(put("/api/v1/services/" + serviceId + "/resources").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"resourceIds\":[" + String.join(",", quoted) + "]}"))
                .andExpect(status().isOk());
    }

    private void setSchedule(String token, UUID resourceId) throws Exception {
        mockMvc.perform(put("/api/v1/resources/" + resourceId + "/availability/1").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ranges\":[{\"start\":\"09:00\",\"end\":\"12:00\"},{\"start\":\"14:00\",\"end\":\"16:00\"}]}"))
                .andExpect(status().isOk());
    }

    private record Client(String email, String token) {
    }

    private Client client() throws Exception {
        String email = uniqueEmail("cliente");
        registerClient(email, uniquePhone());
        return new Client(email, login(email));
    }

    private static String body(UUID serviceId, String date, String start, String end) {
        return "{\"serviceId\":\"" + serviceId + "\",\"date\":\"" + date + "\",\"startTime\":\"" + start
                + "\",\"endTime\":\"" + end + "\"}";
    }

    private MvcResult book(String token, String json) throws Exception {
        var request = post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private List<String> freeSlots(UUID serviceId, LocalDate date) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/services/" + serviceId + "/availability?date=" + date))
                .andExpect(status().isOk()).andReturn();
        List<String> starts = new ArrayList<>();
        json(r).get("slots").forEach(s -> starts.add(s.get("start").asText()));
        return starts;
    }

    @Test
    void unaReservaValidaQuedaConfirmadaConIdYAsociadaAlCliente() throws Exception {
        Fixture f = fixture();
        Client c = client();

        MvcResult result = book(c.token(), body(f.serviceId(), monday().toString(), "10:00", "11:00"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode created = json(result);
        assertThat(created.get("status").asText()).isEqualTo("CONFIRMADA");
        assertThat(created.get("id").asText()).isNotBlank();
        assertThat(created.get("resourceName").asText()).isEqualTo("Sala 1");
        assertThat(created.get("priceCop").asLong()).isEqualTo(25_000L);
        assertThat(created.get("date").asText()).isEqualTo(monday().toString());

        Booking saved = bookingRepository.findById(UUID.fromString(created.get("id").asText())).orElseThrow();
        assertThat(saved.getClientEmail()).isEqualToIgnoringCase(c.email());
        assertThat(saved.getClientId()).isEqualTo(userRepository.findByEmailIgnoreCase(c.email()).orElseThrow().getId());
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
        assertThat(saved.getClientName()).isEqualTo("Usuario de Prueba");
        assertThat(saved.getStartAt()).isEqualTo(monday().atTime(10, 0).atZone(BOGOTA).toInstant());
        assertThat(auditEvents(AuditEventType.CREACION_RESERVA, c.email())).hasSize(1);
    }

    @Test
    void elHorarioReservadoDejaDeAparecerComoLibreEnLaDisponibilidad() throws Exception {
        Fixture f = fixture();
        assertThat(freeSlots(f.serviceId(), monday())).containsExactly("09:00", "10:00", "11:00", "14:00", "15:00");

        book(client().token(), body(f.serviceId(), monday().toString(), "10:00", "11:00"));

        assertThat(freeSlots(f.serviceId(), monday())).containsExactly("09:00", "11:00", "14:00", "15:00");
    }

    @Test
    void unHorarioYaOcupadoDa409YNoSeRegistra() throws Exception {
        Fixture f = fixture();
        book(client().token(), body(f.serviceId(), monday().toString(), "10:00", "11:00"));
        long before = bookingRepository.count();

        MvcResult second = book(client().token(), body(f.serviceId(), monday().toString(), "10:00", "11:00"));

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(second.getResponse().getContentAsString()).contains("no está disponible");
        assertThat(bookingRepository.count()).isEqualTo(before);
    }

    @Test
    void unTraslapeParcialTambienDa409PeroUnHorarioContiguoSiSeAcepta() throws Exception {
        Fixture f = fixture();
        book(client().token(), body(f.serviceId(), monday().toString(), "10:00", "11:00"));

        assertThat(book(client().token(), body(f.serviceId(), monday().toString(), "10:30", "11:30")).getResponse().getStatus())
                .isEqualTo(409);
        assertThat(book(client().token(), body(f.serviceId(), monday().toString(), "09:30", "10:30")).getResponse().getStatus())
                .isEqualTo(409);
        assertThat(book(client().token(), body(f.serviceId(), monday().toString(), "11:00", "12:00")).getResponse().getStatus())
                .isEqualTo(201);
        assertThat(book(client().token(), body(f.serviceId(), monday().toString(), "09:00", "10:00")).getResponse().getStatus())
                .isEqualTo(201);
    }

    @Test
    void unaReservaCanceladaLiberaElHorario() throws Exception {
        Fixture f = fixture();
        MvcResult first = book(client().token(), body(f.serviceId(), monday().toString(), "10:00", "11:00"));
        Booking booking = bookingRepository.findById(UUID.fromString(json(first).get("id").asText())).orElseThrow();
        booking.setStatus(BookingStatus.CANCELADA);
        bookingRepository.save(booking);

        assertThat(freeSlots(f.serviceId(), monday())).contains("10:00");
        assertThat(book(client().token(), body(f.serviceId(), monday().toString(), "10:00", "11:00")).getResponse().getStatus())
                .isEqualTo(201);
    }

    @Test
    void conVariosRecursosElMismoHorarioSeReservaEnOtroRecursoLibre() throws Exception {
        Fixture f = fixture();
        UUID second = createResource(f.token(), f.businessId(), "Sala 2");
        assign(f.token(), f.serviceId(), f.resourceId(), second);
        setSchedule(f.token(), second);

        MvcResult a = book(client().token(), body(f.serviceId(), monday().toString(), "10:00", "11:00"));
        MvcResult b = book(client().token(), body(f.serviceId(), monday().toString(), "10:00", "11:00"));
        MvcResult c = book(client().token(), body(f.serviceId(), monday().toString(), "10:00", "11:00"));

        assertThat(json(a).get("resourceName").asText()).isEqualTo("Sala 1");
        assertThat(json(b).get("resourceName").asText()).isEqualTo("Sala 2");
        assertThat(c.getResponse().getStatus()).isEqualTo(409);
        assertThat(freeSlots(f.serviceId(), monday())).doesNotContain("10:00");
    }

    @Test
    void elClientePuedeElegirElRecursoYUnoNoAsignadoSeRechaza() throws Exception {
        Fixture f = fixture();
        UUID second = createResource(f.token(), f.businessId(), "Sala 2");
        assign(f.token(), f.serviceId(), f.resourceId(), second);
        setSchedule(f.token(), second);
        UUID notAssigned = createResource(f.token(), f.businessId(), "Sala 3");

        String chosen = "{\"serviceId\":\"" + f.serviceId() + "\",\"date\":\"" + monday() + "\",\"startTime\":\"10:00\","
                + "\"endTime\":\"11:00\",\"resourceId\":\"" + second + "\"}";
        assertThat(json(book(client().token(), chosen)).get("resourceName").asText()).isEqualTo("Sala 2");

        String bad = chosen.replace(second.toString(), notAssigned.toString());
        assertThat(book(client().token(), bad).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void fueraDelHorarioDelRecursoDa409() throws Exception {
        Fixture f = fixture();

        for (String[] range : new String[][]{{"12:00", "13:00"}, {"08:00", "09:00"}, {"15:30", "16:30"}, {"17:00", "18:00"}}) {
            MvcResult r = book(client().token(), body(f.serviceId(), monday().toString(), range[0], range[1]));
            assertThat(r.getResponse().getStatus()).as(range[0]).isEqualTo(409);
            assertThat(r.getResponse().getContentAsString()).as(range[0]).contains("fuera del horario");
        }
        // Un día sin horario definido (martes) tampoco admite reservas.
        assertThat(book(client().token(), body(f.serviceId(), monday().plusDays(1).toString(), "10:00", "11:00"))
                .getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void sinFechaDa400ConElCampoDate() throws Exception {
        Fixture f = fixture();

        MvcResult r = book(client().token(),
                "{\"serviceId\":\"" + f.serviceId() + "\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}");

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(r).get("fields").get("date").asText()).contains("obligatoria");
    }

    @Test
    void camposObligatoriosFaltantesDan400PorCampo() throws Exception {
        Client c = client();

        MvcResult r = book(c.token(), "{}");

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        for (String field : new String[]{"serviceId", "date", "startTime", "endTime"}) {
            assertThat(json(r).get("fields").has(field)).as(field).isTrue();
        }
    }

    @Test
    void unaHoraDeFinAnteriorOIgualALaDeInicioDa400() throws Exception {
        Fixture f = fixture();
        Client c = client();

        for (String[] range : new String[][]{{"11:00", "10:00"}, {"10:00", "10:00"}}) {
            MvcResult r = book(c.token(), body(f.serviceId(), monday().toString(), range[0], range[1]));
            assertThat(r.getResponse().getStatus()).as(range[0] + "-" + range[1]).isEqualTo(400);
            assertThat(r.getResponse().getContentAsString()).contains("rango de horas es inválido");
        }
    }

    @Test
    void formatosInvalidosDeFechaYHoraDan400() throws Exception {
        Fixture f = fixture();
        Client c = client();

        assertThat(book(c.token(), body(f.serviceId(), "32/13/2026", "10:00", "11:00")).getResponse().getStatus()).isEqualTo(400);
        assertThat(book(c.token(), body(f.serviceId(), "2026-99-99", "10:00", "11:00")).getResponse().getStatus()).isEqualTo(400);
        assertThat(book(c.token(), body(f.serviceId(), monday().toString(), "25:00", "26:00")).getResponse().getStatus()).isEqualTo(400);
        assertThat(book(c.token(), body(f.serviceId(), monday().toString(), "ab:cd", "11:00")).getResponse().getStatus()).isEqualTo(400);
        assertThat(book(c.token(), "{\"serviceId\":\"no-uuid\",\"date\":\"" + monday() + "\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}")
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(book(c.token(), "").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void unaFechaPasadaDa400() throws Exception {
        Fixture f = fixture();

        MvcResult r = book(client().token(), body(f.serviceId(), LocalDate.now(BOGOTA).minusDays(1).toString(), "10:00", "11:00"));

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString()).contains("fecha futura o la fecha actual");
    }

    @Test
    void laDuracionDebeCoincidirConLaDelServicio() throws Exception {
        Fixture f = fixture();

        MvcResult r = book(client().token(), body(f.serviceId(), monday().toString(), "10:00", "11:30"));

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString()).contains("60 minutos");
    }

    @Test
    void laAntelacionMinimaDelNegocioSeExige() throws Exception {
        Fixture f = fixture();
        mockMvc.perform(put("/api/v1/businesses/" + f.businessId() + "/booking-lead-time")
                .header("Authorization", "Bearer " + f.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"hours\":720}")).andExpect(status().isOk());

        MvcResult r = book(client().token(), body(f.serviceId(), monday().toString(), "10:00", "11:00"));

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString()).contains("antelación");
    }

    @Test
    void unProveedorNoPuedeReservarYSinSesionDa401() throws Exception {
        Fixture f = fixture();

        assertThat(book(f.token(), body(f.serviceId(), monday().toString(), "10:00", "11:00")).getResponse().getStatus()).isEqualTo(403);
        assertThat(book(null, body(f.serviceId(), monday().toString(), "10:00", "11:00")).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void unServicioInexistenteInactivoOConProveedorInactivoDa404() throws Exception {
        Fixture f = fixture();
        Client c = client();

        MvcResult missing = book(c.token(), body(UUID.randomUUID(), monday().toString(), "10:00", "11:00"));
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);

        ServiceOffering service = serviceRepository.findById(f.serviceId()).orElseThrow();
        service.setActive(false);
        serviceRepository.save(service);
        assertThat(book(c.token(), body(f.serviceId(), monday().toString(), "10:00", "11:00")).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void laReservaConservaElPrecioDeCuandoSeHizoAunqueElServicioCambie() throws Exception {
        Fixture f = fixture();
        MvcResult created = book(client().token(), body(f.serviceId(), monday().toString(), "10:00", "11:00"));

        ServiceOffering service = serviceRepository.findById(f.serviceId()).orElseThrow();
        service.setPriceCop(99_000L);
        service.setName("Corte premium");
        serviceRepository.save(service);

        Booking saved = bookingRepository.findById(UUID.fromString(json(created).get("id").asText())).orElseThrow();
        assertThat(saved.getPriceCop()).isEqualTo(25_000L);
        assertThat(saved.getServiceName()).isEqualTo("Corte");
    }

    @Test
    void dosClientesQueReservanElMismoHorarioALaVezUnoGanaYElOtroRecibe409() throws Exception {
        Fixture f = fixture();
        Client a = client();
        Client b = client();
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Client client : new Client[]{a, b}) {
            Callable<Integer> task = () -> {
                ready.countDown();
                go.await();
                return book(client.token(), body(f.serviceId(), monday().toString(), "10:00", "11:00")).getResponse().getStatus();
            };
            futures.add(pool.submit(task));
        }
        ready.await();
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(bookingRepository.findAll().stream()
                .filter(x -> f.serviceId().equals(x.getServiceId())).count()).isEqualTo(1);
    }

    @Test
    void laBaseDeDatosImpideDosReservasConfirmadasTraslapadasAunSinPasarPorLaAplicacion() throws Exception {
        Fixture f = fixture();
        Instant start = monday().atTime(10, 0).atZone(BOGOTA).toInstant();
        Booking first = rawBooking(f, start, start.plusSeconds(3600), BookingStatus.CONFIRMADA);
        bookingRepository.saveAndFlush(first);

        // Mismo recurso, traslape parcial, también CONFIRMADA: la restricción EXCLUDE la rechaza.
        assertThatThrownBy(() -> bookingRepository.saveAndFlush(
                rawBooking(f, start.plusSeconds(1800), start.plusSeconds(5400), BookingStatus.CONFIRMADA)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Una CANCELADA en el mismo horario sí se permite (no ocupa el recurso).
        bookingRepository.saveAndFlush(rawBooking(f, start, start.plusSeconds(3600), BookingStatus.CANCELADA));
    }

    private Booking rawBooking(Fixture f, Instant start, Instant end, BookingStatus status) {
        return Booking.builder().clientEmail("x@example.com").clientName("Cliente X").businessId(f.businessId()).businessName("N")
                .serviceId(f.serviceId()).serviceName("S").resourceId(f.resourceId()).resourceName("R")
                .startAt(start).endAt(end).status(status).priceCop(1L).build();
    }
}
