package com.codefactory.reservas_backend.resource.controller;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

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

/** HU-14 - Registrar recurso (CP-HU14-01..07) contra Spring completo y PostgreSQL real. */
class ResourceCreationIntegrationTest extends AbstractIntegrationTest {

    private record ProviderSession(String email, String token, UUID businessId) {
    }

    private ProviderSession registerProvider() throws Exception {
        String email = uniqueEmail("proveedor");
        String payload = """
                {"fullName":"Proveedor de Prueba","email":"%s","cellphone":"%s","password":"%s","businessName":"Negocio %s"}
                """.formatted(email, uniquePhone(), PASSWORD, UUID.randomUUID().toString().substring(0, 6));
        MvcResult result = mockMvc.perform(post("/api/v1/providers").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn();
        return new ProviderSession(email, login(email), UUID.fromString(json(result).get("businessId").asText()));
    }

    private MvcResult create(UUID businessId, String token, String body) throws Exception {
        var request = post("/api/v1/businesses/" + businessId + "/resources")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static String payload(String name, String type) {
        return "{\"name\":\"" + name + "\",\"type\":\"" + type + "\"}";
    }

    @Test
    void registroValidoDevuelve201ConElRecursoActivoEnSuNegocio() throws Exception {
        ProviderSession p = registerProvider();

        MvcResult result = create(p.businessId(), p.token(), payload("Sala 1", "SALA"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).get("active").asBoolean()).isTrue();
        assertThat(json(result).get("type").asText()).isEqualTo("SALA");
        assertThat(json(result).get("businessId").asText()).isEqualTo(p.businessId().toString());
        assertThat(auditEvents(AuditEventType.REGISTRO_RECURSO, p.email())).hasSize(1);
    }

    @Test
    void losTresTiposSonValidosYSeNormalizanAMayusculas() throws Exception {
        ProviderSession p = registerProvider();

        assertThat(json(create(p.businessId(), p.token(), payload("E1", "equipo"))).get("type").asText()).isEqualTo("EQUIPO");
        assertThat(json(create(p.businessId(), p.token(), payload("P1", "Personal"))).get("type").asText()).isEqualTo("PERSONAL");
    }

    @Test
    void sinNombreDevuelve400ConElCampoName() throws Exception {
        ProviderSession p = registerProvider();

        MvcResult result = create(p.businessId(), p.token(), "{\"type\":\"SALA\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("fields").get("name").asText()).contains("obligatorio");
    }

    @Test
    void sinTipoDevuelve400ConElCampoType() throws Exception {
        ProviderSession p = registerProvider();

        for (String body : new String[]{"{\"name\":\"X\"}", "{\"name\":\"X\",\"type\":\"\"}", "{\"name\":\"X\",\"type\":\"  \"}"}) {
            MvcResult result = create(p.businessId(), p.token(), body);
            assertThat(result.getResponse().getStatus()).as(body).isEqualTo(400);
            assertThat(json(result).get("fields").get("type").asText()).as(body).contains("obligatorio");
        }
    }

    @Test
    void unTipoFueraDeLaListaDevuelve400() throws Exception {
        ProviderSession p = registerProvider();

        MvcResult result = create(p.businessId(), p.token(), payload("Mesa 1", "MESA"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("fields").get("type").asText()).contains("SALA, EQUIPO o PERSONAL");
    }

    @Test
    void nombreDemasiadoLargoDevuelve400() throws Exception {
        ProviderSession p = registerProvider();

        assertThat(create(p.businessId(), p.token(), payload("x".repeat(151), "SALA")).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void nombreDuplicadoSinImportarMayusculasDevuelve409() throws Exception {
        ProviderSession p = registerProvider();
        create(p.businessId(), p.token(), payload("Sala 1", "SALA"));

        MvcResult result = create(p.businessId(), p.token(), payload("  SALA 1 ", "EQUIPO"));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void elMismoNombreEnOtroNegocioEsValido() throws Exception {
        ProviderSession a = registerProvider();
        ProviderSession b = registerProvider();
        create(a.businessId(), a.token(), payload("Sala 1", "SALA"));

        assertThat(create(b.businessId(), b.token(), payload("Sala 1", "SALA")).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void unNegocioEnElCuerpoSeIgnoraYElRecursoQuedaEnElNegocioDeLaRuta() throws Exception {
        ProviderSession p = registerProvider();
        ProviderSession other = registerProvider();
        String body = "{\"name\":\"Sala 9\",\"type\":\"SALA\",\"businessId\":\"" + other.businessId() + "\"}";

        MvcResult result = create(p.businessId(), p.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).get("businessId").asText()).isEqualTo(p.businessId().toString());
        mockMvc.perform(get("/api/v1/businesses/" + other.businessId() + "/resources")
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void negocioAjenoEnLaRutaDevuelve403YNoCreaNada() throws Exception {
        ProviderSession owner = registerProvider();
        ProviderSession other = registerProvider();

        assertThat(create(owner.businessId(), other.token(), payload("Intruso", "SALA")).getResponse().getStatus()).isEqualTo(403);
        mockMvc.perform(get("/api/v1/businesses/" + owner.businessId() + "/resources")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unClienteDevuelve403YNoCreaNada() throws Exception {
        ProviderSession p = registerProvider();
        String clientEmail = uniqueEmail("cliente");
        registerClient(clientEmail, uniquePhone());

        assertThat(create(p.businessId(), login(clientEmail), payload("Sala 1", "SALA")).getResponse().getStatus()).isEqualTo(403);
        mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/resources")
                        .header("Authorization", "Bearer " + p.token()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void sinSesionDevuelve401() throws Exception {
        ProviderSession p = registerProvider();

        assertThat(create(p.businessId(), null, payload("Sala 1", "SALA")).getResponse().getStatus()).isEqualTo(401);
        mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/resources")).andExpect(status().isUnauthorized());
    }

    @Test
    void negocioInexistenteDevuelve404() throws Exception {
        ProviderSession p = registerProvider();

        assertThat(create(UUID.randomUUID(), p.token(), payload("Sala 1", "SALA")).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void elListadoDevuelveLosRecursosEnOrdenDeCreacion() throws Exception {
        ProviderSession p = registerProvider();
        create(p.businessId(), p.token(), payload("Primero", "SALA"));
        create(p.businessId(), p.token(), payload("Segundo", "PERSONAL"));

        mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/resources")
                        .header("Authorization", "Bearer " + p.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Primero"))
                .andExpect(jsonPath("$[1].type").value("PERSONAL"));
    }

    @Test
    void dosRegistrosSimultaneosConElMismoNombreDanUn201YUn409() throws Exception {
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
                return create(p.businessId(), p.token(), payload("Concurrente", "SALA")).getResponse().getStatus();
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
}
