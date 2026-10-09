package com.codefactory.reservas_backend.resource.controller;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

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

/** HU-19 - Definir horarios de atención de un recurso (CP-HU19-01..09) contra PostgreSQL real. */
class ResourceAvailabilityIntegrationTest extends AbstractIntegrationTest {

    private record Provider(String email, String token, UUID businessId) {
    }

    private Provider registerProvider() throws Exception {
        String email = uniqueEmail("proveedor");
        String payload = """
                {"fullName":"Proveedor de Prueba","email":"%s","cellphone":"%s","password":"%s","businessName":"Negocio %s"}
                """.formatted(email, uniquePhone(), PASSWORD, UUID.randomUUID().toString().substring(0, 6));
        MvcResult result = mockMvc.perform(post("/api/v1/providers").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn();
        return new Provider(email, login(email), UUID.fromString(json(result).get("businessId").asText()));
    }

    private UUID createResource(Provider p, String name) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/businesses/" + p.businessId() + "/resources")
                        .header("Authorization", "Bearer " + p.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"SALA\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json(r).get("id").asText());
    }

    private MvcResult putTo(String url, String token, String body) throws Exception {
        var request = put(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult week(UUID resourceId, String token, String body) throws Exception {
        return putTo("/api/v1/resources/" + resourceId + "/availability", token, body);
    }

    private MvcResult day(UUID resourceId, int day, String token, String body) throws Exception {
        return putTo("/api/v1/resources/" + resourceId + "/availability/" + day, token, body);
    }

    private JsonNode schedule(Provider p, UUID resourceId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/resources/" + resourceId + "/availability")
                .header("Authorization", "Bearer " + p.token())).andExpect(status().isOk()).andReturn();
        return json(r);
    }

    private static String ranges(String... startEnd) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < startEnd.length; i += 2) {
            items.add("{\"start\":\"" + startEnd[i] + "\",\"end\":\"" + startEnd[i + 1] + "\"}");
        }
        return "{\"ranges\":[" + String.join(",", items) + "]}";
    }

    private static final String FULL_WEEK = """
            {"days":[
              {"dayOfWeek":1,"ranges":[{"start":"09:00","end":"12:00"},{"start":"14:00","end":"18:00"}]},
              {"dayOfWeek":2,"ranges":[{"start":"09:00","end":"17:00"}]},
              {"dayOfWeek":3,"ranges":[{"start":"09:00","end":"17:00"}]},
              {"dayOfWeek":4,"ranges":[{"start":"09:00","end":"17:00"}]},
              {"dayOfWeek":5,"ranges":[{"start":"09:00","end":"17:00"}]},
              {"dayOfWeek":6,"ranges":[{"start":"10:00","end":"14:00"}]}
            ]}""";

    @Test
    void definirElHorarioCompletoLoGuardaYElDomingoQuedaNoDisponible() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");

        MvcResult result = week(resource, p.token(), FULL_WEEK);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode saved = schedule(p, resource);
        assertThat(saved.get("timezone").asText()).isEqualTo("America/Bogota");
        assertThat(saved.get("days")).hasSize(7);
        assertThat(saved.get("days").get(0).get("ranges")).hasSize(2);
        assertThat(saved.get("days").get(0).get("ranges").get(0).get("start").asText()).isEqualTo("09:00");
        assertThat(saved.get("days").get(5).get("ranges").get(0).get("end").asText()).isEqualTo("14:00");
        assertThat(saved.get("days").get(6).get("dayOfWeek").asInt()).isEqualTo(7);
        assertThat(saved.get("days").get(6).get("ranges")).isEmpty();
        assertThat(auditEvents(AuditEventType.DISPONIBILIDAD_RECURSO, p.email())).hasSize(1);
    }

    @Test
    void unRecursoSinHorarioDevuelveLosSieteDiasSinRangos() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");

        JsonNode saved = schedule(p, resource);

        assertThat(saved.get("days")).hasSize(7);
        saved.get("days").forEach(d -> assertThat(d.get("ranges")).isEmpty());
    }

    @Test
    void editarSoloElLunesNoCambiaLosDemasDias() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");
        week(resource, p.token(), FULL_WEEK);

        MvcResult result = day(resource, 1, p.token(), ranges("08:00", "13:00"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode saved = schedule(p, resource);
        assertThat(saved.get("days").get(0).get("ranges")).hasSize(1);
        assertThat(saved.get("days").get(0).get("ranges").get(0).get("start").asText()).isEqualTo("08:00");
        assertThat(saved.get("days").get(1).get("ranges").get(0).get("end").asText()).isEqualTo("17:00");
        assertThat(saved.get("days").get(5).get("ranges").get(0).get("start").asText()).isEqualTo("10:00");
    }

    @Test
    void dejarUnDiaSinRangosLoMarcaNoDisponible() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");
        week(resource, p.token(), FULL_WEEK);

        assertThat(day(resource, 2, p.token(), "{\"ranges\":[]}").getResponse().getStatus()).isEqualTo(200);

        JsonNode saved = schedule(p, resource);
        assertThat(saved.get("days").get(1).get("ranges")).isEmpty();
        assertThat(saved.get("days").get(2).get("ranges")).hasSize(1);
    }

    @Test
    void elPutDeLaSemanaReemplazaTodoYLosDiasOmitidosQuedanNoDisponibles() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");
        week(resource, p.token(), FULL_WEEK);

        week(resource, p.token(), "{\"days\":[{\"dayOfWeek\":4,\"ranges\":[{\"start\":\"07:00\",\"end\":\"08:00\"}]}]}");

        JsonNode saved = schedule(p, resource);
        assertThat(saved.get("days").get(3).get("ranges")).hasSize(1);
        assertThat(saved.get("days").get(0).get("ranges")).isEmpty();
        assertThat(saved.get("days").get(5).get("ranges")).isEmpty();
    }

    @Test
    void editarElHorarioDeUnRecursoNoAfectaALosOtrosRecursosDelProveedor() throws Exception {
        Provider p = registerProvider();
        UUID a = createResource(p, "Sala A");
        UUID b = createResource(p, "Sala B");
        week(a, p.token(), FULL_WEEK);
        week(b, p.token(), FULL_WEEK);

        day(a, 1, p.token(), "{\"ranges\":[]}");

        assertThat(schedule(p, a).get("days").get(0).get("ranges")).isEmpty();
        assertThat(schedule(p, b).get("days").get(0).get("ranges")).hasSize(2);
    }

    @Test
    void losRangosInvalidosSeRechazanYSeConservaElHorarioAnterior() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");
        week(resource, p.token(), FULL_WEEK);

        String[][] cases = {{"14:00", "10:00"}, {"09:00", "09:00"}, {"25:00", "26:00"}, {"ab:cd", "10:00"},
                {"9:00", "10:00"}, {"09:00", "24:00"}, {"", "10:00"}};
        for (String[] c : cases) {
            MvcResult result = day(resource, 1, p.token(), ranges(c[0], c[1]));
            assertThat(result.getResponse().getStatus()).as("%s - %s", c[0], c[1]).isEqualTo(400);
            assertThat(result.getResponse().getContentAsString()).as("%s - %s", c[0], c[1]).contains("no es válido");
        }
        JsonNode saved = schedule(p, resource);
        assertThat(saved.get("days").get(0).get("ranges")).hasSize(2);
        assertThat(saved.get("days").get(0).get("ranges").get(0).get("start").asText()).isEqualTo("09:00");
    }

    @Test
    void losRangosSuperpuestosSeRechazanYSeConservaElHorarioAnterior() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");
        week(resource, p.token(), FULL_WEEK);

        MvcResult result = day(resource, 1, p.token(), ranges("09:00", "12:00", "11:00", "13:00"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("superponerse");
        assertThat(schedule(p, resource).get("days").get(0).get("ranges")).hasSize(2);
    }

    @Test
    void dosRangosQueSoloComparteElBordeSonValidos() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");

        assertThat(day(resource, 1, p.token(), ranges("09:00", "12:00", "12:00", "14:00")).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void unSemanaConUnDiaInvalidoNoCambiaNada() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");
        week(resource, p.token(), FULL_WEEK);

        String body = """
                {"days":[{"dayOfWeek":1,"ranges":[{"start":"09:00","end":"10:00"}]},
                         {"dayOfWeek":2,"ranges":[{"start":"15:00","end":"10:00"}]}]}""";
        assertThat(week(resource, p.token(), body).getResponse().getStatus()).isEqualTo(400);

        JsonNode saved = schedule(p, resource);
        assertThat(saved.get("days").get(0).get("ranges")).hasSize(2);
        assertThat(saved.get("days").get(5).get("ranges")).hasSize(1);
    }

    @Test
    void diasInvalidosORepetidosDevuelven400() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");

        assertThat(day(resource, 0, p.token(), ranges()).getResponse().getStatus()).isEqualTo(400);
        assertThat(day(resource, 8, p.token(), ranges()).getResponse().getStatus()).isEqualTo(400);
        assertThat(putTo("/api/v1/resources/" + resource + "/availability/lunes", p.token(), ranges()).getResponse().getStatus())
                .isEqualTo(400);
        assertThat(week(resource, p.token(), "{\"days\":[{\"dayOfWeek\":9,\"ranges\":[]}]}").getResponse().getStatus())
                .isEqualTo(400);
        assertThat(week(resource, p.token(), "{\"days\":[{\"dayOfWeek\":1,\"ranges\":[]},{\"dayOfWeek\":1,\"ranges\":[]}]}")
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void cuerposMalFormadosDevuelven400() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");

        for (String body : new String[]{"{}", "{\"days\":null}", "{\"days\":[null]}", "{\"days\":\"x\"}", ""}) {
            assertThat(week(resource, p.token(), body).getResponse().getStatus()).as(body).isEqualTo(400);
        }
        for (String body : new String[]{"{}", "{\"ranges\":null}", "{\"ranges\":[null]}", "{\"ranges\":[{}]}", ""}) {
            assertThat(day(resource, 1, p.token(), body).getResponse().getStatus()).as(body).isEqualTo(400);
        }
    }

    @Test
    void masDeDiezRangosEnUnDiaDevuelve400() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");
        String[] eleven = new String[22];
        for (int i = 0; i < 11; i++) {
            eleven[2 * i] = String.format("%02d:00", i);
            eleven[2 * i + 1] = String.format("%02d:30", i);
        }

        assertThat(day(resource, 1, p.token(), ranges(eleven)).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void unRecursoDeOtroProveedorDevuelve403YNoCambia() throws Exception {
        Provider owner = registerProvider();
        Provider other = registerProvider();
        UUID resource = createResource(owner, "Sala 1");
        week(resource, owner.token(), FULL_WEEK);

        assertThat(day(resource, 1, other.token(), "{\"ranges\":[]}").getResponse().getStatus()).isEqualTo(403);
        assertThat(week(resource, other.token(), "{\"days\":[]}").getResponse().getStatus()).isEqualTo(403);
        mockMvc.perform(get("/api/v1/resources/" + resource + "/availability").header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isForbidden());
        assertThat(schedule(owner, resource).get("days").get(0).get("ranges")).hasSize(2);
    }

    @Test
    void unClienteDevuelve403() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");
        String clientEmail = uniqueEmail("cliente");
        registerClient(clientEmail, uniquePhone());

        assertThat(day(resource, 1, login(clientEmail), "{\"ranges\":[]}").getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void sinSesionDevuelve401() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");

        assertThat(day(resource, 1, null, "{\"ranges\":[]}").getResponse().getStatus()).isEqualTo(401);
        assertThat(week(resource, null, "{\"days\":[]}").getResponse().getStatus()).isEqualTo(401);
        mockMvc.perform(get("/api/v1/resources/" + resource + "/availability")).andExpect(status().isUnauthorized());
    }

    @Test
    void unRecursoInexistenteDevuelve404YUnIdMalFormadoDevuelve400() throws Exception {
        Provider p = registerProvider();

        assertThat(week(UUID.randomUUID(), p.token(), "{\"days\":[]}").getResponse().getStatus()).isEqualTo(404);
        assertThat(putTo("/api/v1/resources/no-es-un-uuid/availability", p.token(), "{\"days\":[]}").getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    void dosEdicionesSimultaneasDelMismoRecursoTerminanBien() throws Exception {
        Provider p = registerProvider();
        UUID resource = createResource(p, "Sala 1");
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Integer> task = () -> {
                ready.countDown();
                go.await();
                return day(resource, 1, p.token(), ranges("09:00", "12:00", "14:00", "18:00")).getResponse().getStatus();
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

        assertThat(statuses).containsExactly(200, 200);
        // Sin el bloqueo del recurso podrían quedar 4 rangos duplicados.
        assertThat(schedule(p, resource).get("days").get(0).get("ranges")).hasSize(2);
    }
}
