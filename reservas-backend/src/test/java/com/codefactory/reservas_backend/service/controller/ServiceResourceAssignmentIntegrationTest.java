package com.codefactory.reservas_backend.service.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-18 - Asignar recursos a un servicio (CP-HU18-01..06) contra Spring completo y PostgreSQL real. */
class ServiceResourceAssignmentIntegrationTest extends AbstractIntegrationTest {

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

    private UUID createService(Provider p, String name) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/businesses/" + p.businessId() + "/services")
                        .header("Authorization", "Bearer " + p.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"durationMinutes\":30,\"priceCop\":10000}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json(r).get("id").asText());
    }

    private UUID createResource(Provider p, String name) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/businesses/" + p.businessId() + "/resources")
                        .header("Authorization", "Bearer " + p.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"SALA\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json(r).get("id").asText());
    }

    private MvcResult assign(UUID serviceId, String token, String body) throws Exception {
        var request = put("/api/v1/services/" + serviceId + "/resources")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static String ids(UUID... resourceIds) {
        List<String> quoted = new ArrayList<>();
        for (UUID id : resourceIds) {
            quoted.add("\"" + id + "\"");
        }
        return "{\"resourceIds\":[" + String.join(",", quoted) + "]}";
    }

    private List<String> assignedNames(Provider p, UUID serviceId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/services/" + serviceId + "/resources")
                .header("Authorization", "Bearer " + p.token())).andExpect(status().isOk()).andReturn();
        List<String> names = new ArrayList<>();
        json(r).get("resources").forEach(n -> names.add(n.get("name").asText()));
        return names;
    }

    @Test
    void asignarUnRecursoLoDejaAsociadoYAuditado() throws Exception {
        Provider p = registerProvider();
        UUID service = createService(p, "Corte");
        UUID sala = createResource(p, "Sala 1");

        MvcResult result = assign(service, p.token(), ids(sala));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("resources")).hasSize(1);
        assertThat(json(result).get("resources").get(0).get("name").asText()).isEqualTo("Sala 1");
        assertThat(assignedNames(p, service)).containsExactly("Sala 1");
        assertThat(auditEvents(AuditEventType.ASIGNACION_RECURSOS, p.email())).hasSize(1);
    }

    @Test
    void asignarVariosRecursosLosDejaTodosAsociados() throws Exception {
        Provider p = registerProvider();
        UUID service = createService(p, "Corte");
        UUID a = createResource(p, "Sala A");
        UUID b = createResource(p, "Sala B");
        UUID c = createResource(p, "Sala C");

        assertThat(assign(service, p.token(), ids(c, a, b)).getResponse().getStatus()).isEqualTo(200);

        assertThat(assignedNames(p, service)).containsExactly("Sala A", "Sala B", "Sala C");
    }

    @Test
    void elPutReemplazaElConjuntoYEsIdempotente() throws Exception {
        Provider p = registerProvider();
        UUID service = createService(p, "Corte");
        UUID a = createResource(p, "Sala A");
        UUID b = createResource(p, "Sala B");
        UUID c = createResource(p, "Sala C");
        assign(service, p.token(), ids(a, b));

        assign(service, p.token(), ids(b, c));
        assertThat(assignedNames(p, service)).containsExactly("Sala B", "Sala C");

        // Repetir la misma petición no cambia nada ni falla.
        assertThat(assign(service, p.token(), ids(b, c)).getResponse().getStatus()).isEqualTo(200);
        assertThat(assignedNames(p, service)).containsExactly("Sala B", "Sala C");
        // Ids repetidos en la lista cuentan una sola vez.
        assertThat(assign(service, p.token(), ids(b, b, c, c)).getResponse().getStatus()).isEqualTo(200);
        assertThat(assignedNames(p, service)).containsExactly("Sala B", "Sala C");
    }

    @Test
    void unaListaVaciaDejaAlServicioSinRecursos() throws Exception {
        Provider p = registerProvider();
        UUID service = createService(p, "Corte");
        assign(service, p.token(), ids(createResource(p, "Sala A")));

        assertThat(assign(service, p.token(), "{\"resourceIds\":[]}").getResponse().getStatus()).isEqualTo(200);

        assertThat(assignedNames(p, service)).isEmpty();
    }

    @Test
    void unRecursoDeOtroNegocioSeRechazaYNoQuedaAsociado() throws Exception {
        Provider p = registerProvider();
        Provider other = registerProvider();
        UUID service = createService(p, "Corte");
        UUID foreign = createResource(other, "Sala ajena");

        MvcResult result = assign(service, p.token(), ids(foreign));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(assignedNames(p, service)).isEmpty();
    }

    @Test
    void unRecursoInexistenteSeRechazaYNoQuedaAsociado() throws Exception {
        Provider p = registerProvider();
        UUID service = createService(p, "Corte");

        MvcResult result = assign(service, p.token(), ids(UUID.randomUUID()));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(assignedNames(p, service)).isEmpty();
    }

    @Test
    void unaMezclaConUnRecursoInvalidoNoAsignaNiSiquieraLosValidosNiTocaLoPrevio() throws Exception {
        Provider p = registerProvider();
        Provider other = registerProvider();
        UUID service = createService(p, "Corte");
        UUID previous = createResource(p, "Previo");
        UUID valid = createResource(p, "Valido");
        UUID foreign = createResource(other, "Ajeno");
        assign(service, p.token(), ids(previous));

        MvcResult result = assign(service, p.token(), ids(valid, foreign));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(assignedNames(p, service)).containsExactly("Previo");
    }

    @Test
    void unServicioDeOtroProveedorDevuelve403YNoCambiaNada() throws Exception {
        Provider owner = registerProvider();
        Provider other = registerProvider();
        UUID service = createService(owner, "Corte");
        UUID mine = createResource(other, "Sala mia");

        assertThat(assign(service, other.token(), ids(mine)).getResponse().getStatus()).isEqualTo(403);
        mockMvc.perform(get("/api/v1/services/" + service + "/resources").header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isForbidden());
        assertThat(assignedNames(owner, service)).isEmpty();
    }

    @Test
    void unClienteDevuelve403() throws Exception {
        Provider p = registerProvider();
        UUID service = createService(p, "Corte");
        String clientEmail = uniqueEmail("cliente");
        registerClient(clientEmail, uniquePhone());

        assertThat(assign(service, login(clientEmail), ids()).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void sinSesionDevuelve401() throws Exception {
        Provider p = registerProvider();
        UUID service = createService(p, "Corte");

        assertThat(assign(service, null, ids()).getResponse().getStatus()).isEqualTo(401);
        mockMvc.perform(get("/api/v1/services/" + service + "/resources")).andExpect(status().isUnauthorized());
    }

    @Test
    void unServicioInexistenteDevuelve404() throws Exception {
        Provider p = registerProvider();

        assertThat(assign(UUID.randomUUID(), p.token(), ids()).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void cuerposInvalidosDevuelven400() throws Exception {
        Provider p = registerProvider();
        UUID service = createService(p, "Corte");

        for (String body : new String[]{"{}", "{\"resourceIds\":null}", "{\"resourceIds\":[\"no-es-uuid\"]}",
                "{\"resourceIds\":[null]}", "{\"resourceIds\":\"x\"}", ""}) {
            assertThat(assign(service, p.token(), body).getResponse().getStatus()).as(body).isEqualTo(400);
        }
    }

    @Test
    void masDeCienRecursosDevuelve400() throws Exception {
        Provider p = registerProvider();
        UUID service = createService(p, "Corte");
        UUID[] many = new UUID[101];
        for (int i = 0; i < many.length; i++) {
            many[i] = UUID.randomUUID();
        }

        assertThat(assign(service, p.token(), ids(many)).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void dosAsignacionesSimultaneasDelMismoServicioTerminanBienYDejanUnEstadoConsistente() throws Exception {
        Provider p = registerProvider();
        UUID service = createService(p, "Corte");
        UUID a = createResource(p, "Sala A");
        UUID b = createResource(p, "Sala B");
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Integer> task = () -> {
                ready.countDown();
                go.await();
                return assign(service, p.token(), ids(a, b)).getResponse().getStatus();
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
        assertThat(assignedNames(p, service)).containsExactly("Sala A", "Sala B");
    }
}
