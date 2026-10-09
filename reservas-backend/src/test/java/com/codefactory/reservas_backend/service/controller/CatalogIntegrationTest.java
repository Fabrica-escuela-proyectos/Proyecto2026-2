package com.codefactory.reservas_backend.service.controller;

import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-13 - Consultar negocios y servicios (CP-HU13-01..07). La base se comparte
 * entre clases de prueba, así que no se asume un catálogo vacío ni un total
 * exacto: el caso "catálogo vacío" se cubre en la prueba unitaria.
 */
class CatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ServiceOfferingRepository serviceRepository;

    private record Provider(String token, UUID businessId, String businessName) {
    }

    private Provider registerProvider() throws Exception {
        String email = uniqueEmail("proveedor");
        String businessName = "Negocio " + UUID.randomUUID().toString().substring(0, 8);
        String payload = """
                {"fullName":"Proveedor de Prueba","email":"%s","cellphone":"%s","password":"%s","businessName":"%s"}
                """.formatted(email, uniquePhone(), PASSWORD, businessName);
        MvcResult result = mockMvc.perform(post("/api/v1/providers").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn();
        return new Provider(login(email), UUID.fromString(json(result).get("businessId").asText()), businessName);
    }

    private String clientToken() throws Exception {
        String email = uniqueEmail("cliente");
        registerClient(email, uniquePhone());
        return login(email);
    }

    private void addService(UUID businessId, String name, boolean active) {
        serviceRepository.save(ServiceOffering.builder().businessId(businessId).name(name).description("Detalle de " + name)
                .durationMinutes(30).priceCop(15_000L).active(active).build());
    }

    private MvcResult getAs(String token, String url) throws Exception {
        var request = get(url);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    @Test
    void unClienteAutenticadoVeLosNegociosPorNombre() throws Exception {
        Provider p = registerProvider();

        MvcResult result = getAs(clientToken(), "/api/v1/businesses?size=50");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        boolean found = false;
        String previous = "";
        for (var item : json(result).get("items")) {
            String name = item.get("name").asText();
            assertThat(name.compareToIgnoreCase(previous)).as("orden por nombre").isGreaterThanOrEqualTo(0);
            previous = name;
            found |= item.get("id").asText().equals(p.businessId().toString());
        }
        assertThat(found || json(result).get("totalElements").asLong() > 50).isTrue();
    }

    @Test
    void elDetalleMuestraSoloLosServiciosActivosConSusDatos() throws Exception {
        Provider p = registerProvider();
        addService(p.businessId(), "Corte", true);
        addService(p.businessId(), "Servicio retirado", false);

        MvcResult result = getAs(clientToken(), "/api/v1/businesses/" + p.businessId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("name").asText()).isEqualTo(p.businessName());
        assertThat(json(result).get("services")).hasSize(1);
        var service = json(result).get("services").get(0);
        assertThat(service.get("name").asText()).isEqualTo("Corte");
        assertThat(service.get("description").asText()).isEqualTo("Detalle de Corte");
        assertThat(service.get("durationMinutes").asInt()).isEqualTo(30);
        assertThat(service.get("priceCop").asLong()).isEqualTo(15_000L);
        assertThat(json(result).get("message").isNull()).isTrue();
    }

    @Test
    void unNegocioSinServiciosDevuelveListaVaciaYMensaje() throws Exception {
        Provider p = registerProvider();
        addService(p.businessId(), "Inactivo", false);

        MvcResult result = getAs(clientToken(), "/api/v1/businesses/" + p.businessId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("services")).isEmpty();
        assertThat(json(result).get("message").asText()).isNotBlank();
    }

    @Test
    void elProveedorTambienPuedeConsultarElCatalogo() throws Exception {
        Provider p = registerProvider();

        assertThat(getAs(p.token(), "/api/v1/businesses/" + p.businessId()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unNegocioInexistenteDevuelve404() throws Exception {
        assertThat(getAs(clientToken(), "/api/v1/businesses/" + UUID.randomUUID()).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void sinSesionDevuelve401() throws Exception {
        assertThat(getAs(null, "/api/v1/businesses").getResponse().getStatus()).isEqualTo(401);
        assertThat(getAs(null, "/api/v1/businesses/" + UUID.randomUUID()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void laPaginacionRespetaElTamanoYRecortaAlTope() throws Exception {
        registerProvider();
        registerProvider();
        registerProvider();
        String token = clientToken();

        MvcResult two = getAs(token, "/api/v1/businesses?page=0&size=2");
        assertThat(json(two).get("items")).hasSize(2);
        assertThat(json(two).get("size").asInt()).isEqualTo(2);
        assertThat(json(two).get("totalElements").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(json(two).get("totalPages").asInt()).isGreaterThanOrEqualTo(2);

        MvcResult huge = getAs(token, "/api/v1/businesses?size=100000");
        assertThat(json(huge).get("size").asInt()).isEqualTo(50);
    }

    @Test
    void unaPaginaFueraDeRangoDevuelveListaVaciaSinError() throws Exception {
        MvcResult result = getAs(clientToken(), "/api/v1/businesses?page=100000&size=10");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("items")).isEmpty();
    }

    @Test
    void parametrosDePaginacionInvalidosDevuelven400() throws Exception {
        String token = clientToken();

        assertThat(getAs(token, "/api/v1/businesses?page=-1").getResponse().getStatus()).isEqualTo(400);
        assertThat(getAs(token, "/api/v1/businesses?size=0").getResponse().getStatus()).isEqualTo(400);
        assertThat(getAs(token, "/api/v1/businesses?size=abc").getResponse().getStatus()).isEqualTo(400);
        assertThat(getAs(token, "/api/v1/businesses/no-es-un-uuid").getResponse().getStatus()).isEqualTo(400);
    }
}
