package com.codefactory.reservas_backend.provider.controller;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-07 - Editar información del negocio, contra Spring completo y PostgreSQL real. */
class BusinessProfileIntegrationTest extends AbstractIntegrationTest {

    private static final String FULL = """
            {"name":"Barbería Central","description":"Cortes clásicos","address":"Calle 10 # 43-12",
             "businessType":"BELLEZA","phone":"6045551234"}
            """;

    private MvcResult edit(String token, UUID businessId, String body) throws Exception {
        var request = patch("/api/v1/businesses/" + businessId).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", bearer(token));
        }
        return mockMvc.perform(request).andReturn();
    }

    private JsonNode detail(String token, UUID businessId) throws Exception {
        return json(mockMvc.perform(get("/api/v1/businesses/" + businessId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn());
    }

    @Test
    void edicionCompletaGuardaTodoEInformaYLaVeElCliente() throws Exception {
        ProviderSetup p = newProvider();

        MvcResult result = edit(p.token(), p.businessId(), FULL);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("message").asText()).isEqualTo("La información del negocio fue actualizada");
        JsonNode seen = detail(newClient().token(), p.businessId());
        assertThat(seen.get("name").asText()).isEqualTo("Barbería Central");
        assertThat(seen.get("description").asText()).isEqualTo("Cortes clásicos");
        assertThat(seen.get("address").asText()).isEqualTo("Calle 10 # 43-12");
        assertThat(seen.get("businessType").asText()).isEqualTo("BELLEZA");
        assertThat(seen.get("phone").asText()).isEqualTo("6045551234");
        assertThat(auditEvents(AuditEventType.EDICION_NEGOCIO, p.email())).hasSize(1);
    }

    @Test
    void editarUnSoloDatoConservaLosDemas() throws Exception {
        ProviderSetup p = newProvider();
        edit(p.token(), p.businessId(), FULL);

        edit(p.token(), p.businessId(), "{\"address\":\"Carrera 70 # 1-2\"}");

        JsonNode seen = detail(p.token(), p.businessId());
        assertThat(seen.get("address").asText()).isEqualTo("Carrera 70 # 1-2");
        assertThat(seen.get("name").asText()).isEqualTo("Barbería Central");
        assertThat(seen.get("phone").asText()).isEqualTo("6045551234");
    }

    @Test
    void losDatosOpcionalesVaciosSeBorran() throws Exception {
        ProviderSetup p = newProvider();
        edit(p.token(), p.businessId(), FULL);

        MvcResult result = edit(p.token(), p.businessId(), "{\"address\":\"\",\"businessType\":\"\",\"phone\":\"\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode seen = detail(p.token(), p.businessId());
        assertThat(seen.get("address").isNull()).isTrue();
        assertThat(seen.get("businessType").isNull()).isTrue();
        assertThat(seen.get("phone").isNull()).isTrue();
        assertThat(seen.get("description").asText()).isEqualTo("Cortes clásicos");
    }

    @Test
    void elNombreVacioSeRechazaYNoSeGuardaNada() throws Exception {
        ProviderSetup p = newProvider();
        edit(p.token(), p.businessId(), FULL);

        MvcResult result = edit(p.token(), p.businessId(), "{\"name\":\"  \",\"address\":\"Otra\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("El nombre del negocio es obligatorio");
        assertThat(detail(p.token(), p.businessId()).get("address").asText()).isEqualTo("Calle 10 # 43-12");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcde12345", "12345", "123", "2001234567", "30012345678"})
    void unTelefonoInvalidoSeRechaza(String phone) throws Exception {
        ProviderSetup p = newProvider();

        MvcResult result = edit(p.token(), p.businessId(), "{\"phone\":\"" + phone + "\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("El teléfono no es válido");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Panadería", "123"})
    void unTipoDeNegocioQueNoExisteSeRechaza(String type) throws Exception {
        ProviderSetup p = newProvider();

        MvcResult result = edit(p.token(), p.businessId(), "{\"businessType\":\"" + type + "\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("El tipo de negocio no es válido");
    }

    @Test
    void laEdicionDeUnNegocioNoTocaLosDeOtroProveedorNiLaPuedeHacerOtro() throws Exception {
        ProviderSetup owner = newProvider();
        ProviderSetup other = newProvider();
        String before = detail(owner.token(), owner.businessId()).get("name").asText();

        assertThat(edit(other.token(), owner.businessId(), FULL).getResponse().getStatus()).isEqualTo(403);
        edit(other.token(), other.businessId(), "{\"name\":\"Otro nombre\"}");

        assertThat(detail(owner.token(), owner.businessId()).get("name").asText()).isEqualTo(before);
    }

    @Test
    void unClienteOSinSesionNoPuedenEditarYUnNegocioInexistenteDa404() throws Exception {
        ProviderSetup p = newProvider();

        assertThat(edit(newClient().token(), p.businessId(), FULL).getResponse().getStatus()).isEqualTo(403);
        assertThat(edit(null, p.businessId(), FULL).getResponse().getStatus()).isEqualTo(401);
        // Un negocio inexistente no es del proveedor: BusinessAccessService responde 404 antes que 403.
        assertThat(edit(p.token(), UUID.randomUUID(), FULL).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void elRegistroDeProveedorSigueFuncionandoConLasColumnasNuevas() throws Exception {
        // V13 añade columnas con NOT NULL (updated_at): el alta de HU-03 debe seguir funcionando.
        String email = uniqueEmail("proveedor");
        mockMvc.perform(post("/api/v1/providers").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"P","email":"%s","cellphone":"%s","password":"%s","businessName":"Nuevo"}
                """.formatted(email, uniquePhone(), PASSWORD))).andExpect(status().isCreated());
    }
}
