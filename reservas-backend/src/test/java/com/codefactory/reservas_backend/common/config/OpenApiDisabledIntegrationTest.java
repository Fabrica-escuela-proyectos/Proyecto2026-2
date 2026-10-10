package com.codefactory.reservas_backend.common.config;

import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ARQ-01 / OWASP A05: con el interruptor apagado (el valor por defecto fuera de dev y test, es decir
 * SWAGGER_ENABLED sin definir) la documentación no se expone: ni la especificación ni la interfaz.
 * Las propiedades distintas crean un contexto de Spring aparte.
 */
@TestPropertySource(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
class OpenApiDisabledIntegrationTest extends AbstractIntegrationTest {

    @Test
    void sinElInterruptorLaEspecificacionYLaInterfazNoExisten() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }

    @Test
    void laApiNormalSigueFuncionando() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
