package com.codefactory.reservas_backend.common.web;

import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OWASP-02 y API-03 con la pila completa (filtros, seguridad y manejador de errores): cabeceras de seguridad,
 * límite de tamaño del cuerpo, correlación de peticiones (X-Request-Id / traceId) y CORS cerrado por defecto.
 */
class WebHardeningIntegrationTest extends AbstractIntegrationTest {

    private static final String USERS = "/api/v1/users";

    // --- Cabeceras de seguridad ---

    @Test
    void lasRespuestasLlevanLasCabecerasDeSeguridad() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", "geolocation=(), camera=(), microphone=()"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }

    @Test
    void tambienLasRespuestasDeErrorLasLlevan() throws Exception {
        mockMvc.perform(get("/api/v1/businesses"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }

    @Test
    void swaggerUiNoLlevaLaPoliticaDeContenidoEstricta() throws Exception {
        // Swagger UI carga scripts y estilos propios; con «default-src 'none'» la interfaz no funcionaría.
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Content-Security-Policy"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    // --- Límite de tamaño del cuerpo ---

    @Test
    void unCuerpoMuyGrandeSeRechazaCon413EnFormatoApiError() throws Exception {
        String grande = "{\"fullName\":\"" + "a".repeat(70_000) + "\"}";

        mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON).content(grande))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.error").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.path").value(USERS))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void unCuerpoDentroDelTopeLlegaALaValidacionNormal() throws Exception {
        mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // --- Correlación: X-Request-Id y traceId ---

    @Test
    void cadaRespuestaTrae_XRequestId_yElErrorLoRepiteEnTraceId() throws Exception {
        String header = mockMvc.perform(get("/api/v1/businesses"))
                .andExpect(status().isUnauthorized())          // lo genera Spring Security, no un controlador
                .andExpect(header().exists("X-Request-Id"))
                .andReturn().getResponse().getHeader("X-Request-Id");

        mockMvc.perform(get("/api/v1/businesses").header("X-Request-Id", header))
                .andExpect(jsonPath("$.traceId").value(header));
    }

    @Test
    void elTraceIdDeUnErrorDeValidacionCoincideConElHeader() throws Exception {
        var result = mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String traceId = json(result).get("traceId").asText();
        assertThat(traceId).isNotBlank();
        assertThat(result.getResponse().getHeader("X-Request-Id")).isEqualTo(traceId);
    }

    @Test
    void unXRequestIdSeguroDelClienteSeRespeta() throws Exception {
        mockMvc.perform(get("/api/v1/businesses").header("X-Request-Id", "cliente-abc-12345"))
                .andExpect(header().string("X-Request-Id", "cliente-abc-12345"))
                .andExpect(jsonPath("$.traceId").value("cliente-abc-12345"));
    }

    @Test
    void unXRequestIdConCaracteresPeligrososSeDescartaYSeGeneraOtro() throws Exception {
        // Un valor libre no debe llegar a los registros (inyección de líneas falsas).
        mockMvc.perform(get("/api/v1/businesses").header("X-Request-Id", "falso 2026 ERROR login admin ok"))
                .andExpect(header().string("X-Request-Id", not("falso 2026 ERROR login admin ok")))
                .andExpect(jsonPath("$.traceId").value(not("falso 2026 ERROR login admin ok")));
        mockMvc.perform(get("/api/v1/businesses").header("X-Request-Id", "corto"))
                .andExpect(header().string("X-Request-Id", not("corto")));
    }

    @Test
    void dosPeticionesRecibenIdentificadoresDistintos() throws Exception {
        String uno = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getHeader("X-Request-Id");
        String dos = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getHeader("X-Request-Id");

        assertThat(uno).isNotBlank().isNotEqualTo(dos);
    }

    // --- CORS cerrado por defecto ---

    @Test
    void sinOrigenesConfiguradosUnOrigenAjenoNoRecibeCabecerasCors() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mockMvc.perform(get("/actuator/health").header("Origin", "https://evil.example"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
