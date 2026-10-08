package com.codefactory.reservas_backend.common.error;

import com.codefactory.reservas_backend.identity.domain.MfaEnrollmentRequiredException;
import com.codefactory.reservas_backend.identity.domain.MfaRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Traducción de excepciones a respuestas de error (errores-api-sprint-1.md y
 * errores-api-sprint-2.md). Cubre la regresión de los issues de Calidad #10
 * (violación de restricción única -> 409, no 500) y #11 (cuerpo vacío o mal
 * formado, UUID inválido, método o tipo de contenido no soportado -> 4xx, no
 * 500), más los códigos nuevos de MFA. Usa un controlador de prueba con
 * MockMvc independiente: no necesita base de datos ni contexto de Spring.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // --- Issue #11: errores de lectura de la solicitud ---

    @Test
    void unCuerpoVacioDebeResponder400YNoElErrorInterno() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud es inválido o está vacío"))
                .andExpect(jsonPath("$.path").value("/probe/body"));
    }

    @Test
    void unJsonMalFormadoDebeResponder400SinExponerElDetalleDelParser() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud es inválido o está vacío"));
    }

    @Test
    void unUuidInvalidoEnLaRutaDebeResponder400NombrandoElParametro() throws Exception {
        mockMvc.perform(get("/probe/uuid/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El parámetro 'id' tiene un formato inválido"));
    }

    @Test
    void unParametroNumericoInvalidoDebeResponder400() throws Exception {
        mockMvc.perform(get("/probe/size").param("size", "muchos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'size' tiene un formato inválido"));
    }

    @Test
    void unParametroObligatorioAusenteDebeResponder400() throws Exception {
        mockMvc.perform(get("/probe/size"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void unaCabeceraObligatoriaAusenteDebeResponder400() throws Exception {
        mockMvc.perform(get("/probe/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void unMetodoNoPermitidoDebeResponder405ConLaCabeceraAllow() throws Exception {
        mockMvc.perform(delete("/probe/body"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unTipoDeContenidoNoSoportadoDebeResponder415() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unaRutaInexistenteDebeResponder404() {
        ResponseEntity<ApiError> response = new GlobalExceptionHandler().handleNoHandler(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/no-existe", "api/v1/no-existe"),
                new MockHttpServletRequest("GET", "/api/v1/no-existe"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getError()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/no-existe");
    }

    // --- Issue #10: violaciones de restricciones de la base de datos ---

    @Test
    void unaViolacionDeLaRestriccionUnicaDelCorreoDebeResponder409() throws Exception {
        mockMvc.perform(get("/probe/unique/uk_users_email"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("El correo electrónico ya está en uso"));
    }

    @Test
    void unaViolacionDeLaRestriccionUnicaDelCelularDebeResponder409() throws Exception {
        mockMvc.perform(get("/probe/unique/uk_users_phone"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El número de celular ya está en uso"));
    }

    @Test
    void otraRestriccionUnicaDebeResponder409Generico() throws Exception {
        mockMvc.perform(get("/probe/unique/uk_providers_user_id"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe un registro con los datos enviados"));
    }

    @Test
    void unaViolacionUnicaSinNombreDeRestriccionDebeResponder409Generico() throws Exception {
        mockMvc.perform(get("/probe/unique-sin-nombre"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe un registro con los datos enviados"));
    }

    @Test
    void unValorDemasiadoLargoParaLaColumnaDebeResponder400() throws Exception {
        mockMvc.perform(get("/probe/too-long"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Alguno de los valores enviados supera la longitud permitida"));
    }

    @Test
    void otraViolacionDeIntegridadSiSigueSiendoUnErrorInternoSinDetalles() throws Exception {
        mockMvc.perform(get("/probe/not-null"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Ocurrió un error interno al procesar la solicitud"));
    }

    // --- Códigos de MFA (ADR-004) ---

    @Test
    void faltaElCodigoMfaDebeResponder401MfaRequired() throws Exception {
        mockMvc.perform(get("/probe/mfa-required"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("MFA_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Se requiere el código MFA"));
    }

    @Test
    void faltaElEnrolamientoDeMfaDebeResponder403MfaEnrollmentRequired() throws Exception {
        mockMvc.perform(get("/probe/mfa-enrollment"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MFA_ENROLLMENT_REQUIRED"))
                .andExpect(jsonPath("$.message").value(MfaEnrollmentRequiredException.DEFAULT_MESSAGE));
    }

    // --- Red de seguridad ---

    @Test
    void unErrorInesperadoNuncaDebeExponerDetallesInternos() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Ocurrió un error interno al procesar la solicitud"))
                .andExpect(jsonPath("$.fields").doesNotExist());
    }

    /** Controlador de prueba: cada ruta provoca una excepción distinta. */
    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        record Payload(String name) {
        }

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        String body(@RequestBody Payload payload) {
            return payload.name();
        }

        @GetMapping("/uuid/{id}")
        String uuid(@PathVariable UUID id) {
            return id.toString();
        }

        @GetMapping("/size")
        String size(@RequestParam int size) {
            return String.valueOf(size);
        }

        @GetMapping("/header")
        String header(@RequestHeader("X-Needed") String needed) {
            return needed;
        }

        @GetMapping("/unique/{constraint}")
        String unique(@PathVariable String constraint) {
            throw new DataIntegrityViolationException("could not execute statement",
                    new SQLException("ERROR: duplicate key value violates unique constraint \"" + constraint
                            + "\"\n  Detail: Key (email)=(alguien@example.com) already exists.", "23505"));
        }

        @GetMapping("/unique-sin-nombre")
        String uniqueSinNombre() {
            throw new DataIntegrityViolationException("could not execute statement", new SQLException("duplicate", "23505"));
        }

        @GetMapping("/too-long")
        String tooLong() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new SQLException("ERROR: value too long for type character varying(150)", "22001"));
        }

        @GetMapping("/not-null")
        String notNull() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new SQLException("ERROR: null value in column \"email\" violates not-null constraint", "23502"));
        }

        @GetMapping("/mfa-required")
        String mfaRequired() {
            throw new MfaRequiredException("Se requiere el código MFA");
        }

        @GetMapping("/mfa-enrollment")
        String mfaEnrollment() {
            throw new MfaEnrollmentRequiredException();
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("select * from users where password_hash = '$2a$10$...'");
        }
    }
}
