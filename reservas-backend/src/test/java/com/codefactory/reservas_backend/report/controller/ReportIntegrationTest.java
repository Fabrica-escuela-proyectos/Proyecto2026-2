package com.codefactory.reservas_backend.report.controller;

import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-29..34 - Reportes, contra Spring completo y PostgreSQL real. Escenario: un recurso con horario de lunes a
 * viernes 08:00-20:00 (720 min/día) y una semana de lunes a domingo = 3600 min disponibles.
 */
class ReportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;

    private MvcResult report(String token, String path) throws Exception {
        var request = get(path);
        if (token != null) {
            request.header("Authorization", bearer(token));
        }
        return mockMvc.perform(request).andReturn();
    }

    private String week(ProviderSetup p, String report) {
        LocalDate monday = upcomingMonday();
        return "/api/v1/businesses/" + p.businessId() + "/reports/" + report + "?from=" + monday + "&to=" + monday.plusDays(6);
    }

    /** Reserva insertada directo (p. ej. COMPLETADA en una fecha futura, imposible por la API). */
    private void raw(ProviderSetup p, ClientSetup c, LocalDate date, int hour, BookingStatus status, long price, String service) {
        var start = date.atTime(LocalTime.of(hour, 0)).atZone(BOGOTA).toInstant();
        bookingRepository.saveAndFlush(Booking.builder().clientId(c.id()).clientEmail(c.email()).clientName("Cliente")
                .businessId(p.businessId()).businessName("N").serviceId(p.serviceId()).serviceName(service)
                .resourceId(p.resourceId()).resourceName("Sala 1").startAt(start).endAt(start.plusSeconds(3600))
                .status(status).priceCop(price).build());
    }

    @Test
    void ocupacionPorRecursoYDelNegocioPorDiaYSemana() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        LocalDate monday = upcomingMonday();
        book(c, p.serviceId(), monday, "08:00", "09:00");
        book(c, p.serviceId(), monday, "09:00", "10:00");
        book(c, p.serviceId(), monday.plusDays(1), "08:00", "09:00");
        UUID empty = newResource(p.token(), p.businessId(), "Sala 2");

        JsonNode byResource = json(report(p.token(), week(p, "resource-occupancy")));
        assertThat(byResource.get("resources")).hasSize(2);
        JsonNode first = byResource.get("resources").get(0);
        assertThat(first.get("resourceId").asText()).isEqualTo(p.resourceId().toString());
        assertThat(first.get("availableMinutes").asLong()).isEqualTo(3600);
        assertThat(first.get("bookedMinutes").asLong()).isEqualTo(180);
        assertThat(first.get("occupancyPercent").asDouble()).isEqualTo(5.0);
        assertThat(byResource.get("resources").get(1).get("resourceId").asText()).isEqualTo(empty.toString());
        assertThat(byResource.get("resources").get(1).get("occupancyPercent").asDouble()).isZero();
        assertThat(byResource.get("occupancyPercent").asDouble()).isEqualTo(2.5);

        JsonNode daily = json(report(p.token(), week(p, "occupancy")));
        assertThat(daily.get("periods")).hasSize(7);
        assertThat(daily.get("periods").get(0).get("bookedMinutes").asLong()).isEqualTo(120);
        assertThat(daily.get("periods").get(5).get("occupancyPercent").isNull()).isTrue();
        assertThat(daily.get("highestDemand").get("start").asText()).isEqualTo(monday.toString());
        assertThat(daily.get("lowestDemand").get("bookedMinutes").asLong()).isZero();

        JsonNode weekly = json(report(p.token(), week(p, "occupancy") + "&groupBy=week"));
        assertThat(weekly.get("groupBy").asText()).isEqualTo("WEEK");
        assertThat(weekly.get("periods")).hasSize(1);
        assertThat(weekly.get("periods").get(0).get("availableMinutes").asLong()).isEqualTo(7200);
    }

    @Test
    void unCierreRestaMinutosDisponiblesDeLaOcupacion() throws Exception {
        ProviderSetup p = newProvider();
        mockMvc.perform(post("/api/v1/resources/" + p.resourceId() + "/closures").header("Authorization", bearer(p.token()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"" + upcomingMonday() + "\"}"))
                .andExpect(status().isCreated());

        JsonNode byResource = json(report(p.token(), week(p, "resource-occupancy")));

        assertThat(byResource.get("resources").get(0).get("availableMinutes").asLong()).isEqualTo(2880);
        assertThat(byResource.get("message").asText()).isEqualTo("No hay reservas en el periodo seleccionado");
    }

    @Test
    void reservasPorEstadoValorYServiciosMasReservados() throws Exception {
        ProviderSetup p = newProvider();
        ClientSetup c = newClient();
        LocalDate monday = upcomingMonday();
        UUID cancelled = book(c, p.serviceId(), monday, "08:00", "09:00");
        mockMvc.perform(post("/api/v1/bookings/" + cancelled + "/cancellation").header("Authorization", bearer(c.token())))
                .andExpect(status().isOk());
        book(c, p.serviceId(), monday, "10:00", "11:00");
        raw(p, c, monday.plusDays(1), 9, BookingStatus.COMPLETADA, 40_000L, "Corte");
        UUID barba = UUID.fromString(json(mockMvc.perform(post("/api/v1/businesses/" + p.businessId() + "/services")
                .header("Authorization", bearer(p.token())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Barba\",\"durationMinutes\":60,\"priceCop\":15000}")).andReturn()).get("id").asText());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/services/" + barba + "/resources")
                .header("Authorization", bearer(p.token())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceIds\":[\"" + p.resourceId() + "\"]}")).andExpect(status().isOk());
        // Un servicio con más reservas sube en el ranking; el renombre se refleja con el nombre vigente.
        for (String h : new String[]{"12", "13", "14"}) {
            book(c, barba, monday, h + ":00", (Integer.parseInt(h) + 1) + ":00");
        }
        mockMvc.perform(patch("/api/v1/services/" + barba).header("Authorization", bearer(p.token()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Barba premium\"}")).andExpect(status().isOk());

        JsonNode byStatus = json(report(p.token(), week(p, "bookings-by-status")));
        assertThat(byStatus.get("confirmed").asLong()).isEqualTo(4);
        assertThat(byStatus.get("cancelled").asLong()).isEqualTo(1);
        assertThat(byStatus.get("completed").asLong()).isEqualTo(1);
        assertThat(byStatus.get("total").asLong()).isEqualTo(6);
        assertThat(byStatus.get("cancelledByOrigin").get("CLIENTE").asLong()).isEqualTo(1);

        JsonNode value = json(report(p.token(), week(p, "booking-value")));
        assertThat(value.get("currency").asText()).isEqualTo("COP");
        assertThat(value.get("completedValue").asLong()).isEqualTo(40_000);
        assertThat(value.get("confirmedValue").asLong()).isEqualTo(25_000 + 3 * 15_000);
        assertThat(value.get("totalValue").asLong()).isEqualTo(40_000 + 25_000 + 45_000);
        assertThat(value.get("cancelledValue").asLong()).isEqualTo(25_000);

        JsonNode top = json(report(p.token(), week(p, "top-services")));
        assertThat(top.get("services")).hasSize(2);
        assertThat(top.get("services").get(0).get("serviceName").asText()).isEqualTo("Barba premium");
        assertThat(top.get("services").get(0).get("bookings").asLong()).isEqualTo(3);
        assertThat(top.get("services").get(1).get("bookings").asLong()).isEqualTo(2);
        assertThat(top.get("services").get(1).get("cancelled").asLong()).isEqualTo(1);
        assertThat(json(report(p.token(), week(p, "top-services") + "&limit=1")).get("services")).hasSize(1);
    }

    @Test
    void sinReservasLosReportesSalenVaciosConMensaje() throws Exception {
        ProviderSetup p = newProvider();

        for (String r : new String[]{"bookings-by-status", "top-services", "booking-value", "occupancy"}) {
            MvcResult result = report(p.token(), week(p, r));
            assertThat(result.getResponse().getStatus()).as(r).isEqualTo(200);
            assertThat(json(result).get("message").asText()).as(r).isEqualTo("No hay reservas en el periodo seleccionado");
        }
    }

    @Test
    void sinFechasUsaLosUltimos30Dias() throws Exception {
        ProviderSetup p = newProvider();

        JsonNode r = json(report(p.token(), "/api/v1/businesses/" + p.businessId() + "/reports/bookings-by-status"));

        LocalDate today = LocalDate.now(BOGOTA);
        assertThat(r.get("to").asText()).isEqualTo(today.toString());
        assertThat(r.get("from").asText()).isEqualTo(today.minusDays(29).toString());
    }

    @Test
    void periodosOParametrosInvalidosDan400() throws Exception {
        ProviderSetup p = newProvider();
        String base = "/api/v1/businesses/" + p.businessId() + "/reports/";
        for (String path : new String[]{"bookings-by-status?from=2026-12-10&to=2026-12-01", "occupancy?from=10-12-2026",
                "booking-value?from=2026-01-01&to=2027-06-01", "occupancy?groupBy=MONTH", "top-services?limit=0",
                "top-services?limit=51"}) {
            assertThat(report(p.token(), base + path).getResponse().getStatus()).as(path).isEqualTo(400);
        }
    }

    @Test
    void soloElProveedorDuenoVeLosReportesDeSuNegocio() throws Exception {
        ProviderSetup owner = newProvider();
        ProviderSetup other = newProvider();
        ClientSetup client = newClient();

        for (String r : new String[]{"resource-occupancy", "occupancy", "bookings-by-status", "top-services", "booking-value"}) {
            assertThat(report(other.token(), week(owner, r)).getResponse().getStatus()).as(r).isEqualTo(403);
            assertThat(report(client.token(), week(owner, r)).getResponse().getStatus()).as(r).isEqualTo(403);
            assertThat(report(null, week(owner, r)).getResponse().getStatus()).as(r).isEqualTo(401);
        }
        assertThat(report(owner.token(), "/api/v1/businesses/" + UUID.randomUUID() + "/reports/booking-value").getResponse()
                .getStatus()).isEqualTo(404);
    }

    @Test
    void elAdministradorVeElResumenDeLaPlataformaYLosDemasNo() throws Exception {
        ProviderSetup p = newProvider();
        book(newClient(), p.serviceId(), upcomingMonday(), "08:00", "09:00");
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin"));
        String path = "/api/v1/admin/reports/platform-summary?from=" + upcomingMonday() + "&to=" + upcomingMonday().plusDays(6);

        MvcResult result = report(admin.token(), path);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.get("activeBusinesses").asLong()).isPositive();
        assertThat(body.get("confirmed").asLong()).isPositive();
        assertThat(body.get("availableMinutes").asLong()).isGreaterThanOrEqualTo(3600);
        assertThat(body.get("occupancyPercent").asDouble()).isPositive();
        assertThat(report(p.token(), path).getResponse().getStatus()).isEqualTo(403);
        assertThat(report(null, path).getResponse().getStatus()).isEqualTo(401);
    }
}
