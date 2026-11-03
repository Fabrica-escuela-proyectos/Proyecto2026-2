package com.codefactory.reservas_backend.service.controller;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-10 (editar), HU-11 (desactivar) y HU-12 (reactivar) un servicio, contra Spring completo y PostgreSQL real. */
class ServiceEditAndLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private ServiceOfferingRepository serviceRepository;

    private MvcResult edit(String token, UUID serviceId, String body) throws Exception {
        var request = patch("/api/v1/services/" + serviceId).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", bearer(token));
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult deactivate(String token, UUID serviceId, String body) throws Exception {
        var request = post("/api/v1/services/" + serviceId + "/deactivation");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (token != null) {
            request.header("Authorization", bearer(token));
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult reactivate(String token, UUID serviceId) throws Exception {
        return mockMvc.perform(post("/api/v1/services/" + serviceId + "/reactivation").header("Authorization", bearer(token))).andReturn();
    }

    // --- HU-10 ---

    @Test
    void editarElPrecioNoCambiaElDeLasReservasYaHechas() throws Exception {
        ProviderSetup p = newProvider();
        UUID booking = book(newClient(), p.serviceId(), upcomingMonday(), "10:00", "11:00");

        MvcResult result = edit(p.token(), p.serviceId(), "{\"priceCop\":60000}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("priceCop").asLong()).isEqualTo(60_000L);
        assertThat(bookingRepository.findById(booking).orElseThrow().getPriceCop()).isEqualTo(25_000L);
        assertThat(auditEvents(AuditEventType.EDICION_SERVICIO, p.email())).hasSize(1);
    }

    @Test
    void edicionCompletaYPrecioCeroSeAceptanYSeVenEnElListado() throws Exception {
        ProviderSetup p = newProvider();

        MvcResult result = edit(p.token(), p.serviceId(),
                "{\"name\":\"Corte clásico\",\"description\":\"Con lavado\",\"durationMinutes\":45,\"priceCop\":0}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var listed = json(mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/services")
                .header("Authorization", bearer(p.token()))).andReturn()).get(0);
        assertThat(listed.get("name").asText()).isEqualTo("Corte clásico");
        assertThat(listed.get("durationMinutes").asInt()).isEqualTo(45);
        assertThat(listed.get("priceCop").asLong()).isZero();
    }

    @Test
    void datosInvalidosSeRechazanYConservanLosAnteriores() throws Exception {
        ProviderSetup p = newProvider();
        for (String body : new String[]{"{\"name\":\"\"}", "{\"durationMinutes\":0}", "{\"durationMinutes\":1441}",
                "{\"priceCop\":-1}", "{\"priceCop\":1000000001}", "{\"description\":\"" + "x".repeat(501) + "\"}"}) {
            assertThat(edit(p.token(), p.serviceId(), body).getResponse().getStatus()).as(body).isEqualTo(400);
        }
        var saved = serviceRepository.findById(p.serviceId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Corte");
        assertThat(saved.getPriceCop()).isEqualTo(25_000L);
    }

    @Test
    void unNombreRepetidoEnElNegocioDa409() throws Exception {
        ProviderSetup p = newProvider();
        mockMvc.perform(post("/api/v1/businesses/" + p.businessId() + "/services").header("Authorization", bearer(p.token()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Barba\",\"durationMinutes\":30,\"priceCop\":10000}"))
                .andExpect(status().isCreated());

        assertThat(edit(p.token(), p.serviceId(), "{\"name\":\"barba\"}").getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void otroProveedorUnClienteOSinSesionNoPuedenEditar() throws Exception {
        ProviderSetup owner = newProvider();
        ProviderSetup other = newProvider();

        assertThat(edit(other.token(), owner.serviceId(), "{\"priceCop\":1}").getResponse().getStatus()).isEqualTo(403);
        assertThat(edit(newClient().token(), owner.serviceId(), "{\"priceCop\":1}").getResponse().getStatus()).isEqualTo(403);
        assertThat(edit(null, owner.serviceId(), "{\"priceCop\":1}").getResponse().getStatus()).isEqualTo(401);
        assertThat(edit(owner.token(), UUID.randomUUID(), "{\"priceCop\":1}").getResponse().getStatus()).isEqualTo(404);
        assertThat(serviceRepository.findById(owner.serviceId()).orElseThrow().getPriceCop()).isEqualTo(25_000L);
    }

    // --- HU-11 ---

    @Test
    void sinReservasFuturasSeDesactivaYYaNoSePuedeReservarNiApareceEnElCatalogo() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();

        MvcResult result = deactivate(p.token(), p.serviceId(), null);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("active").asBoolean()).isFalse();
        MvcResult booking = bookResult(c, p.serviceId(), upcomingMonday(), "10:00", "11:00");
        assertThat(booking.getResponse().getStatus()).isEqualTo(404);
        assertThat(booking.getResponse().getContentAsString()).contains("no está disponible");
        var catalog = json(mockMvc.perform(get("/api/v1/businesses/" + p.businessId()).header("Authorization", bearer(c.token())))
                .andReturn());
        assertThat(catalog.get("services")).isEmpty();
    }

    @Test
    void conReservasFuturasPideConfirmacionYNoCambiaNada() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        List<UUID> ids = List.of(book(c, p.serviceId(), upcomingMonday(), "09:00", "10:00"),
                book(c, p.serviceId(), upcomingMonday(), "11:00", "12:00"),
                book(c, p.serviceId(), upcomingMonday().plusDays(1), "09:00", "10:00"));

        MvcResult result = deactivate(p.token(), p.serviceId(), "{\"confirm\":false}");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(result).get("error").asText()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(json(result).get("fields").get("affectedBookings").asText()).isEqualTo("3");
        assertThat(serviceRepository.findById(p.serviceId()).orElseThrow().isActive()).isTrue();
        ids.forEach(id -> assertThat(bookingRepository.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMADA));
    }

    @Test
    void conConfirmacionCancelaLasFuturasConMotivoServicioNoDisponibleYDesactiva() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        List<UUID> ids = List.of(book(c, p.serviceId(), upcomingMonday(), "09:00", "10:00"),
                book(c, p.serviceId(), upcomingMonday(), "11:00", "12:00"),
                book(c, p.serviceId(), upcomingMonday().plusDays(1), "09:00", "10:00"));

        MvcResult result = deactivate(p.token(), p.serviceId(), "{\"confirm\":true}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("cancelledBookings").asInt()).isEqualTo(3);
        for (UUID id : ids) {
            Booking b = bookingRepository.findById(id).orElseThrow();
            assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELADA);
            assertThat(b.getCancelOrigin()).isEqualTo(CancelOrigin.SERVICIO_NO_DISPONIBLE);
            assertThat(b.getCancelReason()).startsWith("Servicio no disponible");
        }
        assertThat(auditEvents(AuditEventType.DESACTIVACION_SERVICIO, p.email())).hasSize(1);
    }

    @Test
    void otroProveedorNoPuedeDesactivarNiReactivar() throws Exception {
        ProviderSetup owner = newProvider();
        ProviderSetup other = newProvider();

        assertThat(deactivate(other.token(), owner.serviceId(), "{\"confirm\":true}").getResponse().getStatus()).isEqualTo(403);
        assertThat(serviceRepository.findById(owner.serviceId()).orElseThrow().isActive()).isTrue();
        deactivate(owner.token(), owner.serviceId(), null);
        assertThat(reactivate(other.token(), owner.serviceId()).getResponse().getStatus()).isEqualTo(403);
        assertThat(serviceRepository.findById(owner.serviceId()).orElseThrow().isActive()).isFalse();
    }

    /** Carrera HU-11 vs HU-22: con el bloqueo compartido del servicio, ninguna reserva queda CONFIRMADA en un servicio inactivo. */
    @Test
    void unaReservaSimultaneaConLaDesactivacionNoQuedaConfirmadaEnUnServicioInactivo() throws Exception {
        ProviderSetup p = newProvider();
        List<ClientSetup> clients = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            clients.add(newClient());
        }
        LocalDate date = upcomingMonday();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < clients.size(); i++) {
                ClientSetup c = clients.get(i);
                String from = String.format("%02d:00", 9 + i);
                String to = String.format("%02d:00", 10 + i);
                results.add(pool.submit((Callable<Integer>) () -> {
                    start.await();
                    return bookResult(c, p.serviceId(), date, from, to).getResponse().getStatus();
                }));
            }
            results.add(pool.submit((Callable<Integer>) () -> {
                start.await();
                return deactivate(p.token(), p.serviceId(), "{\"confirm\":true}").getResponse().getStatus();
            }));
            start.countDown();
            for (Future<Integer> f : results) {
                assertThat(f.get()).isIn(200, 201, 404);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(serviceRepository.findById(p.serviceId()).orElseThrow().isActive()).isFalse();
        assertThat(bookingRepository.findAll().stream()
                .filter(b -> p.serviceId().equals(b.getServiceId()))
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMADA)).isEmpty();
    }

    // --- HU-12 ---

    @Test
    void reactivarDevuelveElMismoServicioDisponibleYNoRestauraLasCanceladas() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        UUID cancelled = book(c, p.serviceId(), upcomingMonday(), "09:00", "10:00");
        deactivate(p.token(), p.serviceId(), "{\"confirm\":true}");

        MvcResult result = reactivate(p.token(), p.serviceId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("id").asText()).isEqualTo(p.serviceId().toString());
        assertThat(json(result).get("active").asBoolean()).isTrue();
        assertThat(bookingRepository.findById(cancelled).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELADA);
        assertThat(serviceRepository.findAll().stream().filter(s -> p.businessId().equals(s.getBusinessId()))).hasSize(1);
        assertThat(availableStarts(p.serviceId(), upcomingMonday())).contains("09:00");
        book(c, p.serviceId(), upcomingMonday(), "09:00", "10:00");
        assertThat(auditEvents(AuditEventType.REACTIVACION_SERVICIO, p.email())).hasSize(1);
    }

    @Test
    void reactivarUnServicioActivoEsIdempotente() throws Exception {
        ProviderSetup p = newProvider();

        assertThat(reactivate(p.token(), p.serviceId()).getResponse().getStatus()).isEqualTo(200);
        assertThat(auditEvents(AuditEventType.REACTIVACION_SERVICIO, p.email())).isEmpty();
    }
}
