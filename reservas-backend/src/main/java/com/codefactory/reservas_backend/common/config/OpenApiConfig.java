package com.codefactory.reservas_backend.common.config;

import com.codefactory.reservas_backend.common.error.ApiError;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación OpenAPI (ARQ-01 / API-01). Ver docs/api/guia-swagger-openapi.md.
 *
 * Los endpoints solo se publican si {@code springdoc.api-docs.enabled} y
 * {@code springdoc.swagger-ui.enabled} están activos (variable SWAGGER_ENABLED; apagado por defecto,
 * encendido en el perfil dev): en producción la documentación no se expone salvo que se pida (OWASP A05).
 *
 * Este bean solo define lo común: el esquema Bearer (botón Authorize) y las respuestas de error que no
 * dependen de cada endpoint. Lo específico de cada operación se anota en los controladores.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";
    private static final String API_ERROR = "ApiError";
    private static final String JSON = "application/json";

    @Bean
    public OpenAPI reservasOpenApi() {
        OpenAPI api = new OpenAPI()
                .info(new Info()
                        .title("Plataforma de Reservas de Servicios")
                        .version("v1")
                        .description("API REST del backend (Sprint 2). Toda ruta requiere un token JWT "
                                + "(POST /api/v1/auth/login → botón Authorize) salvo las marcadas como públicas. "
                                + "Los errores usan siempre el formato ApiError."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                // Protegidas por defecto; las públicas lo quitan con @SecurityRequirements.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
        api.setTags(OpenApiTags.ALL);
        return api;
    }

    /**
     * Completa la especificación una vez generada: las operaciones protegidas ganan las respuestas 401 y
     * 403, y toda respuesta 4xx/5xx pasa a describir el esquema {@code ApiError}
     * (así los controladores solo escriben el código y la descripción) y las de éxito se declaran como JSON.
     */
    @Bean
    public OpenApiCustomizer errorResponses() {
        return openApi -> {
            registerApiErrorSchema(openApi);
            openApi.getPaths().values().forEach(item -> item.readOperations().forEach(operation -> {
                boolean isPublic = operation.getSecurity() != null && operation.getSecurity().isEmpty();
                if (!isPublic) {
                    // Si el controlador ya describió 401/403 (p. ej. MFA_REQUIRED), se respeta su texto.
                    if (operation.getResponses().get("401") == null) {
                        operation.getResponses().addApiResponse("401", new ApiResponse()
                                .description("Sin sesión, token inválido, expirado o revocado"));
                    }
                    if (operation.getResponses().get("403") == null) {
                        operation.getResponses().addApiResponse("403", new ApiResponse()
                                .description("Rol insuficiente, recurso de otro usuario o MFA pendiente de enrolar"));
                    }
                }
                operation.getResponses().forEach((code, response) -> {
                    if (code.startsWith("4") || code.startsWith("5")) {
                        // springdoc copia a todas las respuestas declaradas el esquema del tipo de retorno
                        // (p. ej. BookingResponse en un 409): se sustituye siempre por el formato de error.
                        response.setContent(new Content().addMediaType(JSON,
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + API_ERROR))));
                    } else if (response.getContent() != null && response.getContent().containsKey("*/*")) {
                        // Todas las respuestas de éxito son JSON; "*/*" solo confunde en la interfaz.
                        Content json = new Content();
                        json.addMediaType(JSON, response.getContent().get("*/*"));
                        response.setContent(json);
                    }
                });
            }));
        };
    }

    private static void registerApiErrorSchema(OpenAPI openApi) {
        ResolvedSchema resolved = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(ApiError.class));
        if (resolved != null && resolved.schema != null) {
            openApi.getComponents().addSchemas(API_ERROR, resolved.schema);
        }
    }
}
