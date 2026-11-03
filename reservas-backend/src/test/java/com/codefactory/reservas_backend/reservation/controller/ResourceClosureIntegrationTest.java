package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.resource.domain.ResourceClosure;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceAvailabilityRepository;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceClosureRepository;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** HU-21 - Bloquear un horario puntual de un recurso, contra Spring completo y PostgreSQL real. */
class ResourceClosureIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private ResourceClosureRepository closureRepository;
    @Autowired
    private ResourceAvailabilityRepository availabilityRepository;

    private MvcResult close(String token, UUID resourceId, String body) throws Exception {
        var request = post("/api/v1/resources/" + resourceId + "/closures").contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", bearer(token));
        }
        return mockMvc.perform(request).andReturn();
    }

    private static String range(LocalDate date, String from, String to) {
        return "{\"start\":\"" + date + " " + from + "\",\"end\":\"" + date + " " + to + "\"}";
    }

    private List<ResourceClosure> closuresOf(UUID resourceId) {
        return closureRepository.findAll().stream().filter(c -> resourceId.equals(c.getResourceId())).toList();
    }

    @Test
    void unCierreSinReservasSeGuardaYQuitaEsosHorariosDeLaDisponibilidad() throws Exception {
        ProviderSetup p = newProvider();
        LocalDate monday = upcomingMonday();

        MvcResult result = close(p.token(), p.resourceId(), range(monday, "14:00", "16:00"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).get("message").asText()).contains("cerrado");
        assertThat(json(result).get("cancelledBookings").asInt()).isZero();
        List<String> slots = availableStarts(p.serviceId(), monday);
        assertThat(slots).doesNotContain("14:00", "15:00").contains("13:00", "16:00");
        assertThat(auditEvents(AuditEventType.CIERRE_RECURSO, p.email())).hasSize(1);
    }

    @Test
    void elHorarioHabitualNoCambiaYLosDiasSiguientesSiguenDisponibles() throws Exception {
        ProviderSetup p = newProvider();
        LocalDate monday = upcomingMonday();
        long rowsBefore = availabilityRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(p.resourceId()).size();

        close(p.token(), p.resourceId(), "{\"date\":\"" + monday + "\"}");

        assertThat(availableStarts(p.serviceId(), monday)).isEmpty();
        assertThat(availableStarts(p.serviceId(), monday.plusDays(7))).contains("08:00");
        assertThat(availabilityRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(p.resourceId())).hasSize((int) rowsBefore);
    }

    @Test
    void conReservasAfectadasPideConfirmacionYSinElla() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        LocalDate monday = upcomingMonday();
        UUID inside = book(c, p.serviceId(), monday, "10:00", "11:00");
        UUID outside = book(c, p.serviceId(), monday, "15:00", "16:00");

        MvcResult result = close(p.token(), p.resourceId(), range(monday, "09:00", "12:00"));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(result).get("error").asText()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(json(result).get("fields").get("affectedBookings").asText()).isEqualTo("1");
        assertThat(closuresOf(p.resourceId())).isEmpty();
        assertThat(bookingRepository.findById(inside).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
        assertThat(bookingRepository.findById(outside).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
    }

    @Test
    void conConfirmacionCancelaSoloLasAfectadasYCierra() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        LocalDate monday = upcomingMonday();
        UUID inside = book(c, p.serviceId(), monday, "10:00", "11:00");
        UUID outside = book(c, p.serviceId(), monday, "15:00", "16:00");

        MvcResult result = close(p.token(), p.resourceId(),
                "{\"start\":\"" + monday + " 09:00\",\"end\":\"" + monday + " 12:00\",\"reason\":\"Mantenimiento\",\"confirm\":true}");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).get("cancelledBookings").asInt()).isEqualTo(1);
        Booking cancelled = bookingRepository.findById(inside).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELADA);
        assertThat(cancelled.getCancelOrigin()).isEqualTo(CancelOrigin.RECURSO_NO_DISPONIBLE);
        assertThat(bookingRepository.findById(outside).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
        assertThat(closuresOf(p.resourceId())).hasSize(1);
        assertThat(closuresOf(p.resourceId()).get(0).getReason()).isEqualTo("Mantenimiento");
    }

    @Test
    void noSePuedeReservarDentroDeUnCierre() throws Exception {
        ProviderSetup p = newProvider();
        LocalDate monday = upcomingMonday();
        close(p.token(), p.resourceId(), range(monday, "10:00", "12:00"));

        MvcResult booking = bookResult(newClient(), p.serviceId(), monday, "10:30", "11:30");

        assertThat(booking.getResponse().getStatus()).isEqualTo(409);
        assertThat(booking.getResponse().getContentAsString()).contains("fuera del horario");
    }

    @ParameterizedTest
    @CsvSource({"14:00,10:00", "09:00,09:00"})
    void unPeriodoConOrdenInvalidoSeRechaza(String from, String to) throws Exception {
        ProviderSetup p = newProvider();

        MvcResult result = close(p.token(), p.resourceId(), range(upcomingMonday(), from, to));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("El periodo de cierre no es válido");
    }

    @Test
    void formatosInvalidosOMezcladosSeRechazan() throws Exception {
        ProviderSetup p = newProvider();
        for (String body : new String[]{"{}", "{\"date\":\"10/11/2026\"}", "{\"start\":\"" + upcomingMonday() + " 10:00\"}",
                "{\"date\":\"" + upcomingMonday() + "\",\"start\":\"" + upcomingMonday() + " 10:00\",\"end\":\"" + upcomingMonday() + " 11:00\"}",
                "{\"start\":\"mañana\",\"end\":\"pasado\"}"}) {
            assertThat(close(p.token(), p.resourceId(), body).getResponse().getStatus()).as(body).isEqualTo(400);
        }
    }

    @Test
    void unaFechaPasadaSeRechaza() throws Exception {
        ProviderSetup p = newProvider();

        MvcResult result = close(p.token(), p.resourceId(), range(LocalDate.now(BOGOTA).minusDays(3), "00:00", "23:59"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("igual o posterior a la fecha actual");
    }

    @Test
    void unCierreQueSeCruzaConOtroSeRechazaPeroUnoContiguoNo() throws Exception {
        ProviderSetup p = newProvider();
        LocalDate monday = upcomingMonday();
        close(p.token(), p.resourceId(), range(monday, "10:00", "12:00"));

        MvcResult overlapping = close(p.token(), p.resourceId(), range(monday, "11:00", "13:00"));
        assertThat(overlapping.getResponse().getStatus()).isEqualTo(409);
        assertThat(overlapping.getResponse().getContentAsString()).contains("se cruza");
        assertThat(close(p.token(), p.resourceId(), range(monday, "12:00", "13:00")).getResponse().getStatus()).isEqualTo(201);
    }

    /** La base (EXCLUDE de V14) impide dos cierres traslapados aunque lleguen a la vez. */
    @Test
    void dosCierresTraslapadosSimultaneosSoloUnoGana() throws Exception {
        ProviderSetup p = newProvider();
        LocalDate monday = upcomingMonday();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String[] r : new String[][]{{"10:00", "12:00"}, {"11:00", "13:00"}}) {
                results.add(pool.submit((Callable<Integer>) () -> {
                    start.await();
                    return close(p.token(), p.resourceId(), range(monday, r[0], r[1])).getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : results) {
                codes.add(f.get());
            }
            assertThat(codes).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(closuresOf(p.resourceId())).hasSize(1);
    }

    @Test
    void elCierreDeUnRecursoNoAfectaAOtroDelMismoProveedor() throws Exception {
        ProviderSetup p = newProvider();
        UUID other = newResource(p.token(), p.businessId(), "Sala 2");
        LocalDate monday = upcomingMonday();

        close(p.token(), p.resourceId(), "{\"date\":\"" + monday + "\"}");

        assertThat(closuresOf(other)).isEmpty();
        assertThat(close(p.token(), other, range(monday, "10:00", "11:00")).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void otroProveedorUnClienteOSinSesionNoPuedenCerrar() throws Exception {
        ProviderSetup owner = newProvider();
        ProviderSetup other = newProvider();
        String body = range(upcomingMonday(), "10:00", "11:00");

        assertThat(close(other.token(), owner.resourceId(), body).getResponse().getStatus()).isEqualTo(403);
        assertThat(close(newClient().token(), owner.resourceId(), body).getResponse().getStatus()).isEqualTo(403);
        assertThat(close(null, owner.resourceId(), body).getResponse().getStatus()).isEqualTo(401);
        assertThat(close(owner.token(), UUID.randomUUID(), body).getResponse().getStatus()).isEqualTo(404);
        assertThat(closuresOf(owner.resourceId())).isEmpty();
    }

    @Test
    void sePuedeListarYEliminarUnCierreFuturoYElRecursoVuelveASuHorario() throws Exception {
        ProviderSetup p = newProvider();
        LocalDate monday = upcomingMonday();
        close(p.token(), p.resourceId(), range(monday, "10:00", "12:00"));

        var listed = json(mockMvc.perform(get("/api/v1/resources/" + p.resourceId() + "/closures")
                .header("Authorization", bearer(p.token()))).andReturn());
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).get("start").asText()).isEqualTo(monday + "T10:00");
        assertThat(listed.get(0).get("fullDay").asBoolean()).isFalse();
        String closureId = listed.get(0).get("id").asText();

        MvcResult deleted = mockMvc.perform(delete("/api/v1/resources/" + p.resourceId() + "/closures/" + closureId)
                .header("Authorization", bearer(p.token()))).andReturn();

        assertThat(deleted.getResponse().getStatus()).isEqualTo(204);
        assertThat(availableStarts(p.serviceId(), monday)).contains("10:00", "11:00");
        assertThat(auditEvents(AuditEventType.ELIMINACION_CIERRE_RECURSO, p.email())).hasSize(1);
    }

    @Test
    void noSePuedeEliminarUnCierreQueYaEmpezoNiUnoAjeno() throws Exception {
        ProviderSetup p = newProvider();
        ProviderSetup other = newProvider();
        UUID started = closureRepository.saveAndFlush(ResourceClosure.builder().resourceId(p.resourceId())
                .startAt(Instant.now().minus(Duration.ofHours(1))).endAt(Instant.now().plus(Duration.ofHours(1))).build()).getId();

        MvcResult result = mockMvc.perform(delete("/api/v1/resources/" + p.resourceId() + "/closures/" + started)
                .header("Authorization", bearer(p.token()))).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(mockMvc.perform(delete("/api/v1/resources/" + p.resourceId() + "/closures/" + started)
                .header("Authorization", bearer(other.token()))).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(delete("/api/v1/resources/" + p.resourceId() + "/closures/" + UUID.randomUUID())
                .header("Authorization", bearer(p.token()))).andReturn().getResponse().getStatus()).isEqualTo(404);
    }
}
