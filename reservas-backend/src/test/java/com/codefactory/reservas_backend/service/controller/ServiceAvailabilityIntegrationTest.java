package com.codefactory.reservas_backend.service.controller;

import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-20 - Consultar disponibilidad de un servicio (CP-HU20-01..10) contra Spring
 * completo y PostgreSQL real. Las reservas todavía no existen (HU-22), así que el
 * descuento de horarios ocupados se prueba en la prueba unitaria del motor.
 */
class ServiceAvailabilityIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private ServiceOfferingRepository serviceRepository;
    @Autowired
    private ResourceRepository resourceRepository;

    private record Fixture(String email, String token, UUID businessId, UUID serviceId, UUID resourceId) {
    }

    private static LocalDate nextMonday() {
        // Un lunes entre 8 y 14 días adelante: siempre futuro, sin importar el día en que corra la prueba.
        return LocalDate.now(BOGOTA).plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    /** Proveedor con negocio, un servicio de 60 min, un recurso asignado y lunes 09:00-12:00 + 14:00-16:00. */
    private Fixture fixture() throws Exception {
        String email = uniqueEmail("proveedor");
        String payload = """
                {"fullName":"Proveedor de Prueba","email":"%s","cellphone":"%s","password":"%s","businessName":"Negocio %s"}
                """.formatted(email, uniquePhone(), PASSWORD, UUID.randomUUID().toString().substring(0, 6));
        MvcResult reg = mockMvc.perform(post("/api/v1/providers").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn();
        UUID businessId = UUID.fromString(json(reg).get("businessId").asText());
        String token = login(email);

        MvcResult s = mockMvc.perform(post("/api/v1/businesses/" + businessId + "/services")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Corte\",\"durationMinutes\":60,\"priceCop\":10000}"))
                .andExpect(status().isCreated()).andReturn();
        UUID serviceId = UUID.fromString(json(s).get("id").asText());

        MvcResult r = mockMvc.perform(post("/api/v1/businesses/" + businessId + "/resources")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sala 1\",\"type\":\"SALA\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID resourceId = UUID.fromString(json(r).get("id").asText());

        mockMvc.perform(put("/api/v1/services/" + serviceId + "/resources").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"resourceIds\":[\"" + resourceId + "\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/resources/" + resourceId + "/availability/1").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ranges\":[{\"start\":\"09:00\",\"end\":\"12:00\"},{\"start\":\"14:00\",\"end\":\"16:00\"}]}"))
                .andExpect(status().isOk());
        return new Fixture(email, token, businessId, serviceId, resourceId);
    }

    private MvcResult query(UUID serviceId, String token, String date) throws Exception {
        var request = get("/api/v1/services/" + serviceId + "/availability" + (date == null ? "" : "?date=" + date));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private String clientToken() throws Exception {
        String email = uniqueEmail("cliente");
        registerClient(email, uniquePhone());
        return login(email);
    }

    private static List<String> starts(JsonNode body) {
        List<String> result = new ArrayList<>();
        body.get("slots").forEach(s -> result.add(s.get("start").asText()));
        return result;
    }

    @Test
    void unClienteVeLosHorariosLibresDefinidosParaEseDia() throws Exception {
        Fixture f = fixture();

        MvcResult result = query(f.serviceId(), clientToken(), nextMonday().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(starts(body)).containsExactly("09:00", "10:00", "11:00", "14:00", "15:00");
        assertThat(body.get("slots").get(0).get("end").asText()).isEqualTo("10:00");
        assertThat(body.get("slots").get(0).get("resources").get(0).get("name").asText()).isEqualTo("Sala 1");
        assertThat(body.get("slots").get(0).get("resources").get(0).get("id").asText()).isEqualTo(f.resourceId().toString());
        assertThat(body.get("message").isNull()).isTrue();
        assertThat(body.get("date").asText()).isEqualTo(nextMonday().toString());
        assertThat(body.get("durationMinutes").asInt()).isEqualTo(60);
        assertThat(body.get("timezone").asText()).isEqualTo("America/Bogota");
    }

    @Test
    void unDiaSinHorarioDefinidoInformaQueNoHayHorarios() throws Exception {
        Fixture f = fixture();

        MvcResult result = query(f.serviceId(), clientToken(), nextMonday().plusDays(1).toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("slots")).isEmpty();
        assertThat(json(result).get("message").asText()).contains("No hay horarios disponibles");
    }

    @Test
    void loscambiosDeHorarioDelRecursoSeVenEnLaConsultaSiguiente() throws Exception {
        Fixture f = fixture();
        String token = clientToken();
        assertThat(starts(json(query(f.serviceId(), token, nextMonday().toString())))).hasSize(5);

        mockMvc.perform(put("/api/v1/resources/" + f.resourceId() + "/availability/1")
                .header("Authorization", "Bearer " + f.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ranges\":[{\"start\":\"08:00\",\"end\":\"10:00\"}]}")).andExpect(status().isOk());

        assertThat(starts(json(query(f.serviceId(), token, nextMonday().toString())))).containsExactly("08:00", "09:00");
    }

    @Test
    void unRecursoInactivoNoAportaHorarios() throws Exception {
        Fixture f = fixture();
        Resource resource = resourceRepository.findById(f.resourceId()).orElseThrow();
        resource.setActive(false);
        resourceRepository.save(resource);

        MvcResult result = query(f.serviceId(), clientToken(), nextMonday().toString());

        assertThat(json(result).get("slots")).isEmpty();
        assertThat(json(result).get("message").asText()).isNotBlank();
    }

    @Test
    void unServicioSinRecursosAsignadosInformaQueNoHayHorarios() throws Exception {
        Fixture f = fixture();
        mockMvc.perform(put("/api/v1/services/" + f.serviceId() + "/resources").header("Authorization", "Bearer " + f.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"resourceIds\":[]}")).andExpect(status().isOk());

        assertThat(json(query(f.serviceId(), clientToken(), nextMonday().toString())).get("slots")).isEmpty();
    }

    @Test
    void laAntelacionMinimaDelNegocioSeDescuenta() throws Exception {
        Fixture f = fixture();
        String token = clientToken();
        // 720 h = 30 días: un lunes a 8-14 días ya no cumple la antelación; uno a 45 días sí.
        mockMvc.perform(put("/api/v1/businesses/" + f.businessId() + "/booking-lead-time")
                .header("Authorization", "Bearer " + f.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"hours\":720}")).andExpect(status().isOk());

        MvcResult near = query(f.serviceId(), token, nextMonday().toString());
        assertThat(json(near).get("slots")).isEmpty();
        assertThat(json(near).get("message").asText()).contains("No quedan horarios");

        LocalDate far = LocalDate.now(BOGOTA).plusDays(45).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        assertThat(starts(json(query(f.serviceId(), token, far.toString())))).hasSize(5);
    }

    @Test
    void sinFechaSeConsultaHoyYNoFalla() throws Exception {
        Fixture f = fixture();

        MvcResult result = query(f.serviceId(), clientToken(), null);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("date").asText()).isEqualTo(LocalDate.now(BOGOTA).toString());
    }

    @Test
    void lasFechasInvalidasPasadasOMuyLejanasDevuelven400() throws Exception {
        Fixture f = fixture();
        String token = clientToken();

        for (String bad : new String[]{"32/13/2026", "ab/cd/efgh", "2026-99-99", "2026-02-30"}) {
            MvcResult r = query(f.serviceId(), token, bad.replace("/", "%2F"));
            assertThat(r.getResponse().getStatus()).as(bad).isEqualTo(400);
            assertThat(r.getResponse().getContentAsString()).as(bad).contains("La fecha no es válida");
        }
        MvcResult past = query(f.serviceId(), token, LocalDate.now(BOGOTA).minusDays(1).toString());
        assertThat(past.getResponse().getStatus()).isEqualTo(400);
        assertThat(past.getResponse().getContentAsString()).contains("fecha futura o la fecha actual");
        assertThat(query(f.serviceId(), token, LocalDate.now(BOGOTA).plusDays(400).toString()).getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    void unServicioInexistenteInactivoOConProveedorInactivoNoEstaDisponible() throws Exception {
        Fixture f = fixture();
        String token = clientToken();

        MvcResult missing = query(UUID.randomUUID(), token, nextMonday().toString());
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);
        assertThat(missing.getResponse().getContentAsString()).contains("El servicio no está disponible");

        ServiceOffering service = serviceRepository.findById(f.serviceId()).orElseThrow();
        service.setActive(false);
        serviceRepository.save(service);
        assertThat(query(f.serviceId(), token, nextMonday().toString()).getResponse().getStatus()).isEqualTo(404);

        service.setActive(true);
        serviceRepository.save(service);
        assertThat(query(f.serviceId(), token, nextMonday().toString()).getResponse().getStatus()).isEqualTo(200);

        User provider = userRepository.findByEmailIgnoreCase(f.email()).orElseThrow();
        provider.setEnabled(false);
        userRepository.save(provider);
        MvcResult disabled = query(f.serviceId(), token, nextMonday().toString());
        assertThat(disabled.getResponse().getStatus()).isEqualTo(404);
        assertThat(disabled.getResponse().getContentAsString()).contains("El servicio no está disponible");
    }

    @Test
    void sinSesionDevuelve401() throws Exception {
        Fixture f = fixture();

        assertThat(query(f.serviceId(), null, nextMonday().toString()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void unIdDeServicioMalFormadoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/services/no-es-uuid/availability").header("Authorization", "Bearer " + clientToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elProveedorTambienPuedeConsultarSuDisponibilidad() throws Exception {
        Fixture f = fixture();

        assertThat(query(f.serviceId(), f.token(), nextMonday().toString()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void dosClientesQueConsultanAlMismoTiempoVenElMismoHorarioLibre() throws Exception {
        Fixture f = fixture();
        String a = clientToken();
        String b = clientToken();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<List<String>>> futures = new ArrayList<>();
        for (String token : new String[]{a, b}) {
            Callable<List<String>> task = () -> {
                ready.countDown();
                go.await();
                return starts(json(query(f.serviceId(), token, nextMonday().toString())));
            };
            futures.add(pool.submit(task));
        }
        ready.await();
        go.countDown();
        List<String> first = futures.get(0).get();
        List<String> second = futures.get(1).get();
        pool.shutdown();

        assertThat(first).isEqualTo(second).containsExactly("09:00", "10:00", "11:00", "14:00", "15:00");
    }
}
