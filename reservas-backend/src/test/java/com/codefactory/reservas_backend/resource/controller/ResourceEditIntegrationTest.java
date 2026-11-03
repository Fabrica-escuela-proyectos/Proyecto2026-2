package com.codefactory.reservas_backend.resource.controller;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/** HU-15 - Editar recurso, contra Spring completo y PostgreSQL real. */
class ResourceEditIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private BookingRepository bookingRepository;

    private MvcResult edit(String token, Object resourceId, String body) throws Exception {
        var request = patch("/api/v1/resources/" + resourceId).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", bearer(token));
        }
        return mockMvc.perform(request).andReturn();
    }

    @Test
    void editarNombreYTipoSeVeEnLasConsultasPosterioresYNoCambiaLasReservas() throws Exception {
        ProviderSetup p = newProvider();
        UUID booking = book(newClient(), p.serviceId(), upcomingMonday(), "10:00", "11:00");

        MvcResult result = edit(p.token(), p.resourceId(), "{\"name\":\"Sala Azul\",\"type\":\"equipo\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var listed = json(mockMvc.perform(get("/api/v1/businesses/" + p.businessId() + "/resources")
                .header("Authorization", bearer(p.token()))).andReturn()).get(0);
        assertThat(listed.get("name").asText()).isEqualTo("Sala Azul");
        assertThat(listed.get("type").asText()).isEqualTo("EQUIPO");
        assertThat(bookingRepository.findById(booking).orElseThrow().getResourceName()).isEqualTo("Sala 1");
        assertThat(auditEvents(AuditEventType.EDICION_RECURSO, p.email())).hasSize(1);
    }

    @Test
    void elNombreVacioOElTipoInvalidoSeRechazanSinGuardar() throws Exception {
        ProviderSetup p = newProvider();

        MvcResult empty = edit(p.token(), p.resourceId(), "{\"name\":\"\"}");
        assertThat(empty.getResponse().getStatus()).isEqualTo(400);
        assertThat(empty.getResponse().getContentAsString()).contains("El nombre del recurso es obligatorio");
        assertThat(edit(p.token(), p.resourceId(), "{\"type\":\"CANCHA\"}").getResponse().getStatus()).isEqualTo(400);
        assertThat(resourceRepository.findById(p.resourceId()).orElseThrow().getName()).isEqualTo("Sala 1");
    }

    @Test
    void unNombreQueYaExisteEnElNegocioDa409() throws Exception {
        ProviderSetup p = newProvider();
        UUID sala2 = newResource(p.token(), p.businessId(), "Sala 2");

        MvcResult result = edit(p.token(), sala2, "{\"name\":\"sala 1\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("Ya existe un recurso con ese nombre");
        assertThat(resourceRepository.findById(sala2).orElseThrow().getName()).isEqualTo("Sala 2");
    }

    @Test
    void cambiarSoloMayusculasDelPropioNombreSePermite() throws Exception {
        ProviderSetup p = newProvider();

        assertThat(edit(p.token(), p.resourceId(), "{\"name\":\"SALA 1\"}").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void elBusinessIdDelCuerpoSeIgnora() throws Exception {
        ProviderSetup p = newProvider();
        ProviderSetup other = newProvider();

        MvcResult result = edit(p.token(), p.resourceId(), "{\"name\":\"Sala X\",\"businessId\":\"" + other.businessId() + "\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(resourceRepository.findById(p.resourceId()).orElseThrow().getBusinessId()).isEqualTo(p.businessId());
    }

    @Test
    void otroProveedorClienteSinSesionEInexistente() throws Exception {
        ProviderSetup owner = newProvider();
        ProviderSetup other = newProvider();

        assertThat(edit(other.token(), owner.resourceId(), "{\"name\":\"Intruso\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(edit(newClient().token(), owner.resourceId(), "{\"name\":\"Intruso\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(edit(null, owner.resourceId(), "{\"name\":\"Intruso\"}").getResponse().getStatus()).isEqualTo(401);
        MvcResult missing = edit(owner.token(), UUID.randomUUID(), "{\"name\":\"X\"}");
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);
        assertThat(missing.getResponse().getContentAsString()).contains("no existe");
        assertThat(resourceRepository.findById(owner.resourceId()).orElseThrow().getName()).isEqualTo("Sala 1");
    }
}
