package com.codefactory.reservas_backend.common.error;

import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regresión de los issues de Calidad #11 (cuerpo vacío o JSON roto devolvía
 * 500) y #9 (sin validación de longitud), con la pila completa: filtros de
 * seguridad, controladores reales y el manejador global de errores. La
 * "tabla del issue" son los tres endpoints públicos de escritura.
 */
class ApiErrorHandlingIntegrationTest extends AbstractIntegrationTest {

    private static final String CLIENT_ENDPOINT = "/api/v1/users";
    private static final String PROVIDER_ENDPOINT = "/api/v1/providers";
    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";

    // --- Issue #11: cuerpo vacío, '{}' y JSON roto en los tres endpoints ---

    @ParameterizedTest(name = "cuerpo vacío en {0}")
    @ValueSource(strings = {CLIENT_ENDPOINT, PROVIDER_ENDPOINT, LOGIN_ENDPOINT})
    void unCuerpoVacioDebeResponder400YNoElErrorInterno(String endpoint) throws Exception {
        mockMvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud es inválido o está vacío"));
    }

    @ParameterizedTest(name = "JSON roto en {0}")
    @ValueSource(strings = {CLIENT_ENDPOINT, PROVIDER_ENDPOINT, LOGIN_ENDPOINT})
    void unJsonRotoDebeResponder400(String endpoint) throws Exception {
        mockMvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content("{\"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest(name = "'{}' en {0}")
    @ValueSource(strings = {CLIENT_ENDPOINT, PROVIDER_ENDPOINT, LOGIN_ENDPOINT})
    void unObjetoVacioDebeResponder400ConLosCamposObligatorios(String endpoint) throws Exception {
        mockMvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.email").exists());
    }

    @ParameterizedTest(name = "tipo de contenido incorrecto en {0}")
    @ValueSource(strings = {CLIENT_ENDPOINT, PROVIDER_ENDPOINT, LOGIN_ENDPOINT})
    void unTipoDeContenidoIncorrectoDebeResponder415(String endpoint) throws Exception {
        mockMvc.perform(post(endpoint).contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // --- Issue #11: rutas, métodos y parámetros con una sesión válida ---

    @Test
    void unUuidInvalidoEnLaRutaDebeResponder400() throws Exception {
        registerClient("errores.uuid@example.com", uniquePhone());
        String token = login("errores.uuid@example.com");

        mockMvc.perform(get("/api/v1/users/abc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El parámetro 'userId' tiene un formato inválido"));
    }

    @Test
    void unaRutaInexistenteDebeResponder404ConSesionValida() throws Exception {
        registerClient("errores.404@example.com", uniquePhone());
        String token = login("errores.404@example.com");

        mockMvc.perform(get("/api/v1/no-existe").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/no-existe"));
    }

    @Test
    void unaRutaInexistenteSinSesionNoRevelaQueNoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void unMetodoNoPermitidoDebeResponder405() throws Exception {
        registerClient("errores.405@example.com", uniquePhone());
        String token = login("errores.405@example.com");

        mockMvc.perform(put(CLIENT_ENDPOINT).header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    // --- Issue #9: longitud máxima de los campos ---

    @Test
    void unRegistroConCadenasMuyLargasDebeResponder400SenalandoLosCampos() throws Exception {
        String payload = """
                {"fullName":"%s","email":"%s@example.com","cellphone":"3001234567","password":"Aa#%s"}
                """.formatted("n".repeat(300), "e".repeat(300), "x".repeat(300));

        mockMvc.perform(post(CLIENT_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.fullName").exists())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void unRegistroDeProveedorConNegocioMuyLargoDebeResponder400() throws Exception {
        String payload = """
                {"fullName":"Carlos","email":"largo.negocio@example.com","cellphone":"3001234567","password":"%s","businessName":"%s"}
                """.formatted(PASSWORD, "b".repeat(151));

        mockMvc.perform(post(PROVIDER_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.businessName").exists());
    }

    @Test
    void unLoginConContrasenaMuyLargaDebeResponder400SinLlegarABcrypt() throws Exception {
        String payload = """
                {"email":"alguien@example.com","password":"%s"}
                """.formatted("p".repeat(5000));

        mockMvc.perform(post(LOGIN_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());
    }
}
