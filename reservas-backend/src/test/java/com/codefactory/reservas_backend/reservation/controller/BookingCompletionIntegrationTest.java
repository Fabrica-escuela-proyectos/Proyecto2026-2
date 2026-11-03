package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** HU-27 - Completar reserva, contra Spring completo y PostgreSQL real. */
class BookingCompletionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;

    private MvcResult complete(String token, Object bookingId) throws Exception {
        var request = post("/api/v1/bookings/" + bookingId + "/completion");
        if (token != null) {
            request.header("Authorization", bearer(token));
        }
        return mockMvc.perform(request).andReturn();
    }

    /** Las reservas pasadas no se pueden crear por la API (antelación): se insertan directo. */
    private UUID rawBooking(ProviderSetup p, ClientSetup c, Instant start, BookingStatus status) {
        return bookingRepository.saveAndFlush(Booking.builder().clientId(c.id()).clientEmail(c.email()).clientName("Cliente")
                .businessId(p.businessId()).businessName("N").serviceId(p.serviceId()).serviceName("Corte")
                .resourceId(p.resourceId()).resourceName("Sala 1").startAt(start).endAt(start.plusSeconds(3600))
                .status(status).priceCop(25_000L).build()).getId();
    }

    @Test
    void elProveedorCompletaUnaReservaQueYaTermino() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        UUID id = rawBooking(p, c, Instant.now().minus(Duration.ofHours(3)), BookingStatus.CONFIRMADA);

        MvcResult result = complete(p.token(), id);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("status").asText()).isEqualTo("COMPLETADA");
        assertThat(bookingRepository.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.COMPLETADA);
        assertThat(auditEvents(AuditEventType.FINALIZACION_RESERVA, p.email())).hasSize(1);
    }

    @Test
    void elClienteVeLaReservaCompletadaEnSuHistorial() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        UUID id = rawBooking(p, c, Instant.now().minus(Duration.ofHours(3)), BookingStatus.CONFIRMADA);
        complete(p.token(), id);

        MvcResult mine = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/bookings/me?status=COMPLETADA").header("Authorization", bearer(c.token()))).andReturn();

        assertThat(json(mine).get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void unaReservaQueNoHaTerminadoOFuturaNoSePuedeCompletar() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        UUID inProgress = rawBooking(p, c, Instant.now().minus(Duration.ofMinutes(30)), BookingStatus.CONFIRMADA);
        UUID future = book(c, p.serviceId(), upcomingMonday(), "10:00", "11:00");

        for (UUID id : new UUID[]{inProgress, future}) {
            MvcResult r = complete(p.token(), id);
            assertThat(r.getResponse().getStatus()).isEqualTo(409);
            assertThat(r.getResponse().getContentAsString()).contains("haya terminado");
            assertThat(bookingRepository.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
        }
    }

    @Test
    void unaCanceladaOYaCompletadaDa409() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        UUID cancelled = rawBooking(p, c, Instant.now().minus(Duration.ofHours(5)), BookingStatus.CANCELADA);
        UUID done = rawBooking(p, c, Instant.now().minus(Duration.ofHours(8)), BookingStatus.COMPLETADA);

        assertThat(complete(p.token(), cancelled).getResponse().getStatus()).isEqualTo(409);
        assertThat(complete(p.token(), done).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void otroProveedorUnClienteOSinSesionNoPuedenCompletar() throws Exception {
        ProviderSetup owner = newProvider();
        ProviderSetup other = newProvider();
        ClientSetup c = newClient();
        UUID id = rawBooking(owner, c, Instant.now().minus(Duration.ofHours(3)), BookingStatus.CONFIRMADA);

        assertThat(complete(other.token(), id).getResponse().getStatus()).isEqualTo(403);
        assertThat(complete(c.token(), id).getResponse().getStatus()).isEqualTo(403);
        assertThat(complete(null, id).getResponse().getStatus()).isEqualTo(401);
        assertThat(complete(owner.token(), UUID.randomUUID()).getResponse().getStatus()).isEqualTo(404);
        assertThat(bookingRepository.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
    }
}
