package com.codefactory.reservas_backend.service.controller;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.audit.domain.AuditLog;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-09 - Crear servicio (CP-HU09-01..10 del plan de trabajo del Sprint 2) contra
 * Spring completo y PostgreSQL real, incluida la creación simultánea con el
 * mismo nombre (el índice único uk_services_business_name es la defensa real).
 */
class ServiceCreationIntegrationTest extends AbstractIntegrationTest {

    /** Un proveedor registrado por la API, con sesión iniciada y su negocio. */
    private record ProviderSession(String email, String token, UUID businessId) {
    }

    private ProviderSession registerProvider() throws Exception {
        String email = uniqueEmail("proveedor");
        String payload = """
                {"fullName":"Proveedor de Prueba","email":"%s","cellphone":"%s","password":"%s","businessName":"Negocio %s"}
                """.formatted(email, uniquePhone(), PASSWORD, UUID.randomUUID().toString().substring(0, 6));
        MvcResult result = mockMvc.perform(post("/api/v1/providers").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn();
        UUID businessId = UUID.fromString(json(result).get("businessId").asText());
        return new ProviderSession(email, login(email), businessId);
    }

    private static String servicePayload(String name, Object duration, Object price) {
        return """
                {"name":"%s","description":"Descripción del servicio","durationMinutes":%s,"priceCop":%s}
                """.formatted(name, duration, price);
    }

    private MvcResult create(UUID businessId, String token, String body) throws Exception {
        var request = post("/api/v1/businesses/" + businessId + "/services")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    @Test
    void creacionValidaDevuelve201ConElServicioActivo() throws Exception {
        ProviderSession p = registerProvider();

        MvcResult result = create(p.businessId(), p.token(), servicePayload("Corte de cabello", 45, 30000));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).get("active").asBoolean()).isTrue();
        assertThat(json(result).get("businessId").asText()).isEqualTo(p.businessId().toString());
        assertThat(json(result).get("priceCop").asLong()).isEqualTo(30000);
        assertThat(auditEvents(AuditEventType.CREACION_SERVICIO, p.email())).hasSize(1);
    }

    @Test
    void precioCeroEsUnServicioGratuitoValido() throws Exception {
        ProviderSession p = registerProvider();

        MvcResult result = create(p.businessId(), p.token(), servicePayload("Asesoría inicial", 30, 0));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).get("priceCop").asLong()).isZero();
    }

    @Test
    void camposObligatoriosFaltantesDevuelven400PorCampo() throws Exception {
        ProviderSession p = registerProvider();

        MvcResult result = create(p.businessId(), p.token(), "{}");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("fields").has("name")).isTrue();
        assertThat(json(result).get("fields").has("durationMinutes")).isTrue();
        assertThat(json(result).get("fields").has("priceCop")).isTrue();
    }

    @Test
    void duracionInvalidaDevuelve400() throws Exception {
        ProviderSession p = registerProvider();

        assertThat(create(p.businessId(), p.token(), servicePayload("A1", 0, 1000)).getResponse().getStatus()).isEqualTo(400);
        assertThat(create(p.businessId(), p.token(), servicePayload("A2", -30, 1000)).getResponse().getStatus()).isEqualTo(400);
        assertThat(create(p.businessId(), p.token(), servicePayload("A3", 1441, 1000)).getResponse().getStatus()).isEqualTo(400);
        assertThat(create(p.businessId(), p.token(), servicePayload("A4", "\"abc\"", 1000)).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void precioInvalidoDevuelve400() throws Exception {
        ProviderSession p = registerProvider();

        assertThat(create(p.businessId(), p.token(), servicePayload("B1", 30, -5000)).getResponse().getStatus()).isEqualTo(400);
        assertThat(create(p.businessId(), p.token(), servicePayload("B2", 30, "\"abc\"")).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void nombreDemasiadoLargoDevuelve400() throws Exception {
        ProviderSession p = registerProvider();

        MvcResult result = create(p.businessId(), p.token(), servicePayload("x".repeat(151), 30, 1000));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("fields").has("name")).isTrue();
    }

    @Test
    void nombreRepetidoSinImportarMayusculasDevuelve409() throws Exception {
        ProviderSession p = registerProvider();
        create(p.businessId(), p.token(), servicePayload("Corte", 30, 1000));

        MvcResult result = create(p.businessId(), p.token(), servicePayload("  CORTE  ", 30, 2000));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void elMismoNombreEnOtroNegocioEsValido() throws Exception {
        ProviderSession a = registerProvider();
        ProviderSession b = registerProvider();
        create(a.businessId(), a.token(), servicePayload("Corte", 30, 1000));

        MvcResult result = create(b.businessId(), b.token(), servicePayload("Corte", 30, 1000));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void negocioAjenoDevuelve403YNoCreaNada() throws Exception {
        ProviderSession owner = registerProvider();
        ProviderSession other = registerProvider();

        MvcResult result = create(owner.businessId(), other.token(), servicePayload("Intruso", 30, 1000));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        mockMvc.perform(get("/api/v1/businesses/" + owner.businessId() + "/services")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void clienteDevuelve403() throws Exception {
        ProviderSession p = registerProvider();
        String clientEmail = uniqueEmail("cliente");
        registerClient(clientEmail, uniquePhone());

        MvcResult result = create(p.businessId(), login(clientEmail), servicePayload("Corte", 30, 1000));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void sinSesionDevuelve401() throws Exception {
        ProviderSession p = registerProvider();

        assertThat(create(p.businessId(), null, servicePayload("Corte", 30, 1000)).getResponse().getStatus()).isEqualTo(401);
        mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/services")).andExpect(status().isUnauthorized());
    }

    @Test
    void negocioInexistenteDevuelve404() throws Exception {
        ProviderSession p = registerProvider();

        MvcResult result = create(UUID.randomUUID(), p.token(), servicePayload("Corte", 30, 1000));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void idDeNegocioConFormatoInvalidoDevuelve400() throws Exception {
        ProviderSession p = registerProvider();

        mockMvc.perform(post("/api/v1/businesses/no-es-un-uuid/services")
                        .header("Authorization", "Bearer " + p.token())
                        .contentType(MediaType.APPLICATION_JSON).content(servicePayload("Corte", 30, 1000)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elListadoDevuelveLosServiciosDelNegocioEnOrdenDeCreacion() throws Exception {
        ProviderSession p = registerProvider();
        create(p.businessId(), p.token(), servicePayload("Primero", 30, 1000));
        create(p.businessId(), p.token(), servicePayload("Segundo", 60, 2000));

        mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/services")
                        .header("Authorization", "Bearer " + p.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Primero"))
                .andExpect(jsonPath("$[1].name").value("Segundo"));
    }

    @Test
    void dosCreacionesSimultaneasConElMismoNombreDanUn201YUn409() throws Exception {
        ProviderSession p = registerProvider();
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Integer> task = () -> {
                ready.countDown();
                go.await();
                return create(p.businessId(), p.token(), servicePayload("Concurrente", 30, 1000))
                        .getResponse().getStatus();
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

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }

    /** CP-HU09-01-E1/E2/E3 - Cada campo obligatorio que falta se señala por separado y no se crea el servicio. */
    @Test
    void cadaCampoObligatorioFaltanteSeSenalaPorSeparado() throws Exception {
        ProviderSession p = registerProvider();
        String[] bodies = {
                "{\"durationMinutes\":45,\"priceCop\":35000}",
                "{\"name\":\"Corte de cabello\",\"priceCop\":35000}",
                "{\"name\":\"Corte de cabello\",\"durationMinutes\":45}"};
        String[] missing = {"name", "durationMinutes", "priceCop"};

        for (int i = 0; i < bodies.length; i++) {
            MvcResult result = create(p.businessId(), p.token(), bodies[i]);
            assertThat(result.getResponse().getStatus()).as(missing[i]).isEqualTo(400);
            assertThat(json(result).get("fields").has(missing[i])).as(missing[i]).isTrue();
        }
        mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/services")
                        .header("Authorization", "Bearer " + p.token()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** CP-HU09-01-E4/E5/E7 - Duración 0 o negativa y precio negativo: 400 con el mensaje del campo y nada se crea. */
    @Test
    void duracionCeroNegativaYPrecioNegativoNombranElCampo() throws Exception {
        ProviderSession p = registerProvider();

        for (int duration : new int[]{0, -30}) {
            MvcResult result = create(p.businessId(), p.token(), servicePayload("D" + duration, duration, 1000));
            assertThat(result.getResponse().getStatus()).as("duración %s", duration).isEqualTo(400);
            assertThat(json(result).get("fields").has("durationMinutes")).as("duración %s", duration).isTrue();
        }
        MvcResult negativePrice = create(p.businessId(), p.token(), servicePayload("P1", 30, -5000));
        assertThat(negativePrice.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(negativePrice).get("fields").has("priceCop")).isTrue();

        mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/services")
                        .header("Authorization", "Bearer " + p.token()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** CP-HU09-02 (camino feliz) - Un servicio de 0 pesos es gratuito y el cliente lo ve con su duración y precio. */
    @Test
    void unServicioGratuitoLoVeElClienteConSuDuracionYPrecioCero() throws Exception {
        ProviderSession p = registerProvider();
        MvcResult created = create(p.businessId(), p.token(), servicePayload("Primera valoración", 30, 0));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String clientEmail = uniqueEmail("cliente");
        registerClient(clientEmail, uniquePhone());

        MvcResult detail = mockMvc.perform(get("/api/v1/businesses/" + p.businessId())
                .header("Authorization", "Bearer " + login(clientEmail))).andReturn();

        assertThat(detail.getResponse().getStatus()).isEqualTo(200);
        var service = json(detail).get("services").get(0);
        assertThat(service.get("name").asText()).isEqualTo("Primera valoración");
        assertThat(service.get("durationMinutes").asInt()).isEqualTo(30);
        assertThat(service.get("priceCop").asLong()).isZero();
    }

    /** CP-HU09-03 (camino feliz) - La creación queda registrada con quién y cuándo, sin contraseñas ni tokens. */
    @Test
    void laCreacionQuedaAuditadaConAutorYFechaSinDatosSensibles() throws Exception {
        ProviderSession p = registerProvider();
        Instant before = Instant.now().minusSeconds(60);

        create(p.businessId(), p.token(), servicePayload("Manicure", 40, 25000));

        List<AuditLog> events = auditEvents(AuditEventType.CREACION_SERVICIO, p.email());
        assertThat(events).hasSize(1);
        AuditLog event = events.get(0);
        assertThat(event.getSubjectEmail()).isEqualToIgnoringCase(p.email());
        assertThat(event.getOutcome()).isEqualTo("SUCCESS");
        assertThat(event.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(60));
        String detail = String.valueOf(event.getDetail());
        assertThat(detail).doesNotContain(PASSWORD).doesNotContain(p.token()).doesNotContainIgnoringCase("password");
    }

    /** CP-HU09-03-E4 - Con la sesión vencida (más de 1 hora) se responde 401 y no se crea el servicio. */
    @Test
    void unaSesionExpiradaRecibe401YNoCreaElServicio() throws Exception {
        ProviderSession p = registerProvider();
        expireSession(p.token());

        assertThat(create(p.businessId(), p.token(), servicePayload("Manicure", 40, 25000)).getResponse().getStatus())
                .isEqualTo(401);

        mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/services")
                        .header("Authorization", "Bearer " + login(p.email())))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
