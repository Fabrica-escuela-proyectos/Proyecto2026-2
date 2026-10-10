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
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-28 - Cancelar reservas futuras al eliminar un usuario (CP-HU28-01..06) con eliminación real por un administrador con MFA. */
class AccountDeletionIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private BookingRepository bookingRepository;

    private record Provider(String email, String token, UUID userId, UUID businessId, UUID serviceId) {
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
        UUID userId = UUID.fromString(json(reg).get("userId").asText());
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
        return new Provider(email, token, userId, businessId, serviceId);
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

    private MvcResult deleteUser(AdminSession admin, UUID userId) throws Exception {
        return mockMvc.perform(delete("/api/v1/users/" + userId).header("Authorization", "Bearer " + admin.token())
                .header("X-MFA-Code", admin.code())).andReturn();
    }

    private Booking booking(UUID id) {
        return bookingRepository.findById(id).orElseThrow();
    }

    private UUID rawBooking(Provider p, Client c, Instant start, BookingStatus status) {
        return bookingRepository.saveAndFlush(Booking.builder().clientId(c.id()).clientEmail(c.email()).clientName("Cliente")
                .businessId(p.businessId()).businessName("N").serviceId(p.serviceId()).serviceName("S").resourceName("R")
                .startAt(start).endAt(start.plusSeconds(3600)).status(status).priceCop(1L).build()).getId();
    }

    @Test
    void alEliminarUnClienteSusReservasFuturasQuedanCanceladasPorEliminacionDeCuentaYLasDeOtrosNoSeTocan() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin"));
        Provider p = provider();
        Client leaving = client();
        Client staying = client();
        UUID mine1 = book(leaving, p, "09:00", "10:00");
        UUID mine2 = book(leaving, p, "10:00", "11:00");
        UUID others = book(staying, p, "11:00", "12:00");

        MvcResult result = deleteUser(admin, leaving.id());

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(userRepository.findById(leaving.id())).isEmpty();
        for (UUID id : new UUID[]{mine1, mine2}) {
            Booking b = booking(id);
            assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELADA);
            assertThat(b.getCancelOrigin()).isEqualTo(CancelOrigin.ELIMINACION_CUENTA);
            assertThat(b.getCancelReason()).contains("cliente fue eliminada");
            assertThat(b.getCancelledAt()).isNotNull();
        }
        assertThat(booking(others).getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
        assertThat(auditEvents(AuditEventType.CANCELACION_RESERVA, leaving.email())).hasSize(1);
        assertThat(auditEvents(AuditEventType.ELIMINACION_USUARIO, leaving.email())).hasSize(1);
    }

    @Test
    void laReservaCanceladaConservaElHistorialDelClienteEliminadoYElProveedorLaVe() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin"));
        Provider p = provider();
        Client leaving = client();
        UUID id = book(leaving, p, "10:00", "11:00");

        deleteUser(admin, leaving.id());

        Booking b = booking(id);
        assertThat(b.getClientId()).isNull();
        assertThat(b.getClientEmail()).isEqualToIgnoringCase(leaving.email());
        assertThat(b.getClientName()).isEqualTo("Usuario de Prueba");
        assertThat(b.getServiceName()).isEqualTo("Corte");
        assertThat(b.getPriceCop()).isEqualTo(25_000L);
        JsonNode item = json(mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/bookings")
                .header("Authorization", "Bearer " + p.token())).andReturn()).get("items").get(0);
        assertThat(item.get("status").asText()).isEqualTo("CANCELADA");
        assertThat(item.get("cancelOrigin").asText()).isEqualTo("ELIMINACION_CUENTA");
        assertThat(item.get("clientId").isNull()).isTrue();
        assertThat(item.get("clientName").asText()).isEqualTo("Usuario de Prueba");
    }

    @Test
    void alEliminarUnProveedorSeCancelanLasReservasFuturasDeSusClientesYSusServiciosDejanDeSerReservables() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin"));
        Provider p = provider();
        Client c1 = client();
        Client c2 = client();
        UUID b1 = book(c1, p, "09:00", "10:00");
        UUID b2 = book(c2, p, "10:00", "11:00");

        MvcResult result = deleteUser(admin, p.userId());

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        for (UUID id : new UUID[]{b1, b2}) {
            Booking b = booking(id);
            assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELADA);
            assertThat(b.getCancelOrigin()).isEqualTo(CancelOrigin.ELIMINACION_CUENTA);
            assertThat(b.getCancelReason()).contains("proveedor fue eliminada");
            // Historial: aunque el negocio y el servicio ya no existen, la reserva conserva sus copias.
            assertThat(b.getBusinessId()).isNull();
            assertThat(b.getServiceId()).isNull();
            assertThat(b.getServiceName()).isEqualTo("Corte");
            assertThat(b.getBusinessName()).startsWith("Negocio ");
        }
        // Los servicios del proveedor ya no existen: no se puede consultar ni reservar.
        assertThat(mockMvc.perform(get("/api/v1/services/" + p.serviceId() + "/availability?date=" + monday()))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(post("/api/v1/bookings").header("Authorization", "Bearer " + c1.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":\"" + p.serviceId() + "\",\"date\":\"" + monday() + "\",\"startTime\":\"12:00\",\"endTime\":\"13:00\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        // El cliente ve su reserva cancelada con el motivo.
        JsonNode mine = json(mockMvc.perform(get("/api/v1/bookings/me").header("Authorization", "Bearer " + c1.token()))
                .andReturn()).get("items").get(0);
        assertThat(mine.get("status").asText()).isEqualTo("CANCELADA");
        assertThat(mine.get("cancelOrigin").asText()).isEqualTo("ELIMINACION_CUENTA");
    }

    @Test
    void laCancelacionPorEliminacionNoAplicaLaReglaDeUnaHora() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin"));
        Provider p = provider();
        Client leaving = client();
        UUID soon = rawBooking(p, leaving, Instant.now().plus(Duration.ofMinutes(10)), BookingStatus.CONFIRMADA);

        deleteUser(admin, leaving.id());

        assertThat(booking(soon).getStatus()).isEqualTo(BookingStatus.CANCELADA);
    }

    @Test
    void lasReservasPasadasCompletadasYYaCanceladasSeConservanComoHistorial() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin"));
        Provider p = provider();
        Client leaving = client();
        UUID past = rawBooking(p, leaving, Instant.now().minus(Duration.ofDays(5)), BookingStatus.CONFIRMADA);
        UUID done = rawBooking(p, leaving, Instant.now().plus(Duration.ofDays(2)), BookingStatus.COMPLETADA);
        UUID cancelled = rawBooking(p, leaving, Instant.now().plus(Duration.ofDays(3)), BookingStatus.CANCELADA);

        assertThat(deleteUser(admin, leaving.id()).getResponse().getStatus()).isEqualTo(204);

        assertThat(booking(past).getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
        assertThat(booking(done).getStatus()).isEqualTo(BookingStatus.COMPLETADA);
        assertThat(booking(cancelled).getStatus()).isEqualTo(BookingStatus.CANCELADA);
        assertThat(booking(cancelled).getCancelOrigin()).isNull();
        // Siguen existiendo (no se borran en cascada), solo pierden la referencia al usuario.
        assertThat(booking(past).getClientId()).isNull();
        assertThat(booking(past).getClientEmail()).isEqualToIgnoringCase(leaving.email());
    }

    @Test
    void eliminarUnUsuarioSinReservasSigueFuncionando() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin"));
        Client c = client();

        assertThat(deleteUser(admin, c.id()).getResponse().getStatus()).isEqualTo(204);
        assertThat(userRepository.findById(c.id())).isEmpty();
        assertThat(auditEvents(AuditEventType.CANCELACION_RESERVA, c.email())).isEmpty();
    }

    @Test
    void unAdministradorNoPuedeEliminarOtroAdministradorYNoSeTocaNada() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin"));
        AdminSession other = createEnrolledAdmin(uniqueEmail("admin2"));

        MvcResult result = deleteUser(admin, other.userId());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(userRepository.findById(other.userId())).isPresent();
    }
}
