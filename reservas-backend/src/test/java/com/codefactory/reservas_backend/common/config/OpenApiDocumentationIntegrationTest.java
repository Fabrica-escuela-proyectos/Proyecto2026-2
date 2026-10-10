package com.codefactory.reservas_backend.common.config;

import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ARQ-01: la especificación OpenAPI se genera completa y no se rompe en silencio. Es la red de seguridad
 * contra dos descuidos: un endpoint nuevo sin documentar y una actualización de springdoc/Spring que deje de
 * generar la especificación. Corre con el perfil test, que enciende springdoc.
 */
class OpenApiDocumentationIntegrationTest extends AbstractIntegrationTest {

    private static final String APP_PACKAGE = "com.codefactory.reservas_backend";

    /** Rutas públicas del API (SecurityConfig): no deben pedir el candado de Swagger. */
    private static final Set<String> PUBLIC_OPERATIONS = Set.of(
            "POST /api/v1/auth/login",
            "POST /api/v1/users",
            "POST /api/v1/providers",
            "GET /api/v1/services/{serviceId}/availability");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void laEspecificacionEsOpenApi31ConEsquemaBearerYApiError() throws Exception {
        JsonNode spec = spec();

        assertThat(spec.get("openapi").asText()).startsWith("3.");
        assertThat(spec.at("/info/title").asText()).isEqualTo("Plataforma de Reservas de Servicios");
        assertThat(spec.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(spec.at("/components/securitySchemes/bearerAuth/bearerFormat").asText()).isEqualTo("JWT");
        assertThat(spec.at("/components/schemas/ApiError/properties").propertyNames())
                .contains("timestamp", "status", "error", "message", "path", "fields");
        // Ejemplos de los DTO de entrada (se ven en "Try it out") y validaciones de Bean Validation.
        assertThat(spec.at("/components/schemas/CreateBookingRequest/properties/startTime/example").asText()).isEqualTo("10:00");
        assertThat(spec.at("/components/schemas/CreateBookingRequest/properties/startTime/pattern").asText()).isNotBlank();
        assertThat(spec.at("/components/schemas/CreateServiceRequest/properties/durationMinutes/maximum").asInt()).isEqualTo(1440);
    }

    @Test
    void todoEndpointDeLaAplicacionEstaDocumentadoConResumenYGrupo() throws Exception {
        JsonNode spec = spec();
        List<String> sinDocumentar = new ArrayList<>();
        Set<String> grupos = new HashSet<>();
        spec.get("tags").forEach(t -> grupos.add(t.get("name").asText()));

        for (String operacion : appOperations()) {
            String[] partes = operacion.split(" ", 2);
            JsonNode op = spec.at("/paths").path(partes[1]).path(partes[0].toLowerCase());
            if (op.isMissingNode()) {
                sinDocumentar.add(operacion + " (no aparece en la especificación)");
                continue;
            }
            if (op.path("summary").asText().isBlank()) {
                sinDocumentar.add(operacion + " (sin @Operation.summary)");
            }
            if (op.path("tags").isEmpty() || !grupos.contains(op.path("tags").get(0).asText())) {
                sinDocumentar.add(operacion + " (sin @Tag de OpenApiTags)");
            }
        }

        assertThat(sinDocumentar).as("endpoints sin documentar en Swagger").isEmpty();
        assertThat(appOperations()).hasSizeGreaterThanOrEqualTo(33);
    }

    @Test
    void soloLasRutasPublicasQuitanElCandadoYLasProtegidasDeclaran401y403() throws Exception {
        JsonNode spec = spec();

        for (Map.Entry<String, JsonNode> path : spec.get("paths").properties()) {
            for (Map.Entry<String, JsonNode> method : path.getValue().properties()) {
                String operacion = method.getKey().toUpperCase() + " " + path.getKey();
                JsonNode op = method.getValue();
                boolean publica = PUBLIC_OPERATIONS.contains(operacion);
                JsonNode security = op.get("security");
                if (publica) {
                    assertThat(security).as("security de " + operacion).isNotNull();
                    assertThat(security.isEmpty()).as("%s debe ir sin candado", operacion).isTrue();
                    assertThat(op.path("responses").has("401")).as("401 de la ruta pública " + operacion).isEqualTo(
                            operacion.equals("POST /api/v1/auth/login"));
                } else {
                    // Protegida: hereda el requisito Bearer global (sin security propio) y declara 401 y 403.
                    assertThat(security == null || !security.isEmpty()).as("%s debe pedir token", operacion).isTrue();
                    assertThat(op.path("responses").has("401")).as("401 de " + operacion).isTrue();
                    assertThat(op.path("responses").has("403")).as("403 de " + operacion).isTrue();
                    assertThat(op.at("/responses/403/content/application~1json/schema/$ref").asText())
                            .as("el 403 de %s describe ApiError", operacion).endsWith("/ApiError");
                }
                // Ningún error describe el tipo de la respuesta de éxito (springdoc lo copia si no se corrige) y
                // las de éxito con cuerpo son JSON.
                for (Map.Entry<String, JsonNode> respuesta : op.get("responses").properties()) {
                    String codigo = respuesta.getKey();
                    JsonNode contenido = respuesta.getValue().path("content");
                    if (codigo.startsWith("4") || codigo.startsWith("5")) {
                        assertThat(contenido.at("/application~1json/schema/$ref").asText())
                                .as("%s %s", operacion, codigo).endsWith("/ApiError");
                    } else if (!contenido.isMissingNode()) {
                        assertThat(contenido.propertyNames()).as("%s %s", operacion, codigo).containsExactly("application/json");
                    }
                }
            }
        }
    }

    @Test
    void lasRespuestasDeErrorDeclaradasPorLosControladoresDescribenApiError() throws Exception {
        JsonNode crear = spec().at("/paths/~1api~1v1~1bookings/post/responses");

        assertThat(crear.propertyNames()).contains("201", "400", "404", "409", "401", "403");
        assertThat(crear.at("/409/content/application~1json/schema/$ref").asText()).endsWith("/ApiError");
        assertThat(crear.at("/201/description").asText()).isEqualTo("Reserva CONFIRMADA");
    }

    @Test
    void elHeaderDeConfirmacionMfaApareceUnaSolaVezEnLasOperacionesSensibles() throws Exception {
        JsonNode spec = spec();

        for (String ruta : List.of("/paths/~1api~1v1~1users~1{userId}~1role/patch", "/paths/~1api~1v1~1users~1{userId}/delete")) {
            long cabeceras = 0;
            for (JsonNode parametro : spec.at(ruta).path("parameters")) {
                if ("X-MFA-Code".equals(parametro.path("name").asText())) {
                    cabeceras++;
                    assertThat(parametro.path("in").asText()).isEqualTo("header");
                }
            }
            assertThat(cabeceras).as("X-MFA-Code en " + ruta).isEqualTo(1);
        }
    }

    @Test
    void swaggerUiSeSirveSinTokenConSpringdocEncendido() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    private JsonNode spec() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    /** "MÉTODO /ruta" de cada endpoint implementado por un controlador de la aplicación (excluye actuator, springdoc, error). */
    private Set<String> appOperations() {
        Set<String> operaciones = new HashSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = e.getValue();
            if (!handler.getBeanType().getName().startsWith(APP_PACKAGE)) {
                continue;
            }
            Set<String> rutas = e.getKey().getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> metodos = e.getKey().getMethodsCondition().getMethods();
            for (String ruta : rutas) {
                for (RequestMethod metodo : metodos) {
                    operaciones.add(metodo.name() + " " + ruta);
                }
            }
        }
        return operaciones;
    }
}
