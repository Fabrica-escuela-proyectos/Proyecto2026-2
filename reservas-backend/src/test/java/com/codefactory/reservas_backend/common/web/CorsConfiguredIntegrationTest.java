package com.codefactory.reservas_backend.common.web;

import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OWASP-02: con CORS_ALLOWED_ORIGINS definido, solo esos orígenes reciben cabeceras CORS (un contexto de Spring
 * aparte por la propiedad distinta).
 */
@TestPropertySource(properties = "security.cors.allowed-origins=https://app.example.com, https://admin.example.com")
class CorsConfiguredIntegrationTest extends AbstractIntegrationTest {

    @Test
    void unOrigenAutorizadoRecibeLasCabecerasEnElPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,x-mfa-code"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void elSegundoOrigenDeLaListaTambienSeAutoriza() throws Exception {
        mockMvc.perform(get("/actuator/health").header("Origin", "https://admin.example.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://admin.example.com"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Request-Id"));
    }

    @Test
    void unOrigenQueNoEstaEnLaListaSeRechaza() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
