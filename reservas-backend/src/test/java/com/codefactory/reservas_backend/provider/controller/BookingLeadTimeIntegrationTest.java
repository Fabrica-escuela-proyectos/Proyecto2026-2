package com.codefactory.reservas_backend.provider.controller;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.provider.application.BusinessSettingsService;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-08 - Definir antelación mínima de reserva (CP-HU08-01..06) contra PostgreSQL real. */
class BookingLeadTimeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BusinessSettingsService businessSettingsService;

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

    private MvcResult putLead(UUID businessId, String token, String body) throws Exception {
        var request = put("/api/v1/businesses/" + businessId + "/booking-lead-time")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private int currentHours(ProviderSession p) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/booking-lead-time")
                .header("Authorization", "Bearer " + p.token())).andExpect(status().isOk()).andReturn();
        return json(result).get("hours").asInt();
    }

    @Test
    void unNegocioNuevoTieneUnaHoraPorDefecto() throws Exception {
        ProviderSession p = registerProvider();

        assertThat(currentHours(p)).isEqualTo(1);
        assertThat(businessSettingsService.minAdvanceHoursOf(p.businessId())).isEqualTo(1);
    }

    @Test
    void elDuenioPuedeDefinirDosHorasYQuedaAuditado() throws Exception {
        ProviderSession p = registerProvider();

        MvcResult result = putLead(p.businessId(), p.token(), "{\"hours\":2}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("hours").asInt()).isEqualTo(2);
        assertThat(currentHours(p)).isEqualTo(2);
        assertThat(auditEvents(AuditEventType.CONFIGURACION_NEGOCIO, p.email())).hasSize(1);
    }

    @Test
    void sePuedenUsarLosLimitesDelRango() throws Exception {
        ProviderSession p = registerProvider();

        assertThat(putLead(p.businessId(), p.token(), "{\"hours\":720}").getResponse().getStatus()).isEqualTo(200);
        assertThat(putLead(p.businessId(), p.token(), "{\"hours\":1}").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void losValoresInvalidosDan400YConservanElAnterior() throws Exception {
        ProviderSession p = registerProvider();
        putLead(p.businessId(), p.token(), "{\"hours\":3}");

        for (String body : new String[]{"{\"hours\":0}", "{\"hours\":-2}", "{\"hours\":721}", "{\"hours\":\"abc\"}",
                "{}", "{\"hours\":null}", ""}) {
            assertThat(putLead(p.businessId(), p.token(), body).getResponse().getStatus())
                    .as("cuerpo %s", body).isEqualTo(400);
        }
        assertThat(currentHours(p)).isEqualTo(3);
    }

    @Test
    void otroProveedorRecibe403YNoCambiaNada() throws Exception {
        ProviderSession owner = registerProvider();
        ProviderSession other = registerProvider();

        assertThat(putLead(owner.businessId(), other.token(), "{\"hours\":5}").getResponse().getStatus()).isEqualTo(403);
        mockMvc.perform(get("/api/v1/businesses/" + owner.businessId() + "/booking-lead-time")
                .header("Authorization", "Bearer " + other.token())).andExpect(status().isForbidden());
        assertThat(currentHours(owner)).isEqualTo(1);
    }

    @Test
    void unClienteRecibe403() throws Exception {
        ProviderSession p = registerProvider();
        String clientEmail = uniqueEmail("cliente");
        registerClient(clientEmail, uniquePhone());

        assertThat(putLead(p.businessId(), login(clientEmail), "{\"hours\":2}").getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void sinSesionRecibe401() throws Exception {
        ProviderSession p = registerProvider();

        assertThat(putLead(p.businessId(), null, "{\"hours\":2}").getResponse().getStatus()).isEqualTo(401);
        mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/booking-lead-time"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unNegocioInexistenteRecibe404() throws Exception {
        ProviderSession p = registerProvider();

        assertThat(putLead(UUID.randomUUID(), p.token(), "{\"hours\":2}").getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void elGetDevuelveElFormatoEsperado() throws Exception {
        ProviderSession p = registerProvider();

        mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/booking-lead-time")
                        .header("Authorization", "Bearer " + p.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(p.businessId().toString()))
                .andExpect(jsonPath("$.hours").value(1));
    }
}
