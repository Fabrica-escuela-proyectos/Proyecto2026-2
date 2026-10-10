# Guía: documentar la API con Swagger / OpenAPI

**Estado:** esta guía describe **cómo hacerlo**; todavía **no está aplicada en el repositorio** (es la tarea `ARQ-01`, `API-01` y `API-02` de Azure). Todo lo que sigue se **probó** en una copia aparte del proyecto (Spring Boot 4.1.1, Java 17, PostgreSQL 16): compiló, la aplicación arrancó y los resultados que se citan salieron de ese ensayo. Lo que no se probó está marcado como tal.

## 1. Qué se obtiene y por qué conviene

- **OpenAPI 3.1** generado desde el código (`/v3/api-docs`) y una **interfaz Swagger UI** (`/swagger-ui.html`) donde se ve cada endpoint, su cuerpo, sus validaciones y se puede **probar en vivo** con un token.
- Es un entregable explícito de Arquisoft en el Sprint 2 y sirve para la sustentación: se muestra la API funcionando sin abrir Postman.
- No reemplaza a [`endpoints-sprint-2.md`](endpoints-sprint-2.md) (reglas de negocio y ejemplos) ni a [`referencia-api-sprint-2.md`](referencia-api-sprint-2.md) (matriz de acceso y DTO, generada del código): los complementa con algo ejecutable.

## 2. Qué hace springdoc por sí solo (probado)

Con solo agregar la dependencia, **sin tocar ningún controlador**:

| Resultado del ensayo | |
|---|---|
| Versión | `springdoc-openapi-starter-webmvc-ui` **3.1.1** (última en Maven Central a 2026-10; las 3.0.x también existen). Funciona con **Spring Boot 4.1.1 y Jackson 3**: la aplicación arrancó sin cambios. |
| Cobertura | Detectó los **33 endpoints** (27 rutas) y **46 esquemas** automáticamente. |
| Validaciones | Las de Bean Validation salen solas en el esquema: `required`, `minLength`, `pattern`, `maxLength`… (p. ej. `CreateBookingRequest.startTime` con su patrón `HH:mm`). |

Y qué **no** hace bien por defecto (por eso hay que anotar):

| Falta | Consecuencia |
|---|---|
| Solo documenta la respuesta `200` | Un `POST` que devuelve `201`, o los `400/404/409`, no aparecen |
| Las etiquetas salen como `booking-controller`, `my-bookings-controller`… | Poco legible; hay que agruparlas por tema |
| Las rutas públicas no se distinguen | Swagger les pide el candado igual |
| Sin resúmenes ni descripciones | Quedan solo el método y la ruta |
| El rol que exige `@PreAuthorize` no aparece | Hay que decirlo en la descripción |

## 3. Pasos (en este orden)

### Paso 1 — Dependencia (`reservas-backend/pom.xml`)

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.1.1</version>
</dependency>
```

### Paso 2 — Configuración global (clase nueva `common/config/OpenApiConfig.java`)

Define el título, el **esquema Bearer JWT** (el botón *Authorize*) y las respuestas `401`/`403` comunes a todas las rutas protegidas. Código probado:

```java
@Configuration
public class OpenApiConfig {

    /** Respuestas comunes a todas las operaciones protegidas: 401 y 403 con el formato ApiError. */
    @Bean
    public OpenApiCustomizer commonErrorResponses() {
        return openApi -> openApi.getPaths().values().forEach(item -> item.readOperations().forEach(op -> {
            if (op.getSecurity() == null || !op.getSecurity().isEmpty()) {      // las públicas llevan security = []
                op.getResponses().addApiResponse("401", new ApiResponse().description("Sin sesión o token inválido (ApiError)"));
                op.getResponses().addApiResponse("403", new ApiResponse().description("Rol insuficiente o recurso de otro usuario (ApiError)"));
            }
        }));
    }

    @Bean
    public OpenAPI reservasOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Plataforma de Reservas de Servicios").version("v1")
                        .description("API REST del backend (Sprint 2). Formato de error uniforme: ApiError."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));   // todas protegidas salvo que se diga lo contrario
    }
}
```
(Imports: `io.swagger.v3.oas.models.*`, `org.springdoc.core.customizers.OpenApiCustomizer`; `ApiResponse` aquí es `io.swagger.v3.oas.models.responses.ApiResponse`.)

### Paso 3 — Permitir las rutas de la documentación (`SecurityConfig`)

Sin esto responden `401`. Junto a las demás reglas públicas:

```java
.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
```
Con esto `GET /swagger-ui.html` redirige (302) a `/swagger-ui/index.html` y responde `200` (probado).

### Paso 4 — Decidir si queda abierto en producción (**decisión de seguridad, OWASP A05**)

Springdoc lo deja **activo por defecto** y avisa en el log: *«SpringDoc /v3/api-docs endpoint is enabled by default. To disable it in production, set the property 'springdoc.api-docs.enabled=false'»* (y `springdoc.swagger-ui.enabled=false` para la interfaz). Recomendación: interruptor por variable de entorno, apagado por defecto y encendido en el entorno donde se vaya a mostrar:

```yaml
# application.yml
springdoc:
  api-docs:
    enabled: ${SWAGGER_ENABLED:false}
  swagger-ui:
    enabled: ${SWAGGER_ENABLED:false}
```
En `application-dev.yml` ponerlas en `true`; en Render, la variable `SWAGGER_ENABLED=true` **solo** mientras dure la demostración. *(No se probó apagarlo; las dos propiedades son las que nombra el propio springdoc.)* Argumento para dejarlo abierto: el repositorio es público y la API ya está documentada en `docs/`; el riesgo es solo facilitar el reconocimiento de la superficie. Decisión pendiente del equipo (ver [decisiones](../arquitectura/decisiones-y-supuestos-sprint-2.md)).

### Paso 5 — Anotar los controladores

Lo mínimo útil por controlador y por operación (todo probado salvo lo que se indique):

```java
@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Reservas", description = "Crear, consultar y cancelar reservas (HU-22 a HU-26)")   // agrupa en la interfaz
public class BookingController {

    @Operation(summary = "Crear una reserva (HU-22)",
            description = "Solo el rol CLIENTE. El cliente sale de la sesión. La duración debe ser la del servicio.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reserva CONFIRMADA creada"),
            @ApiResponse(responseCode = "400", description = "Validación: fecha, horas, duración, antelación o recurso",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "El servicio no está disponible",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Horario ocupado o fuera del horario del recurso",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))})
    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<BookingResponse> create(...) { ... }
}
```

Para una **ruta pública** (HU-20 y las de registro/login), quita el candado con `@SecurityRequirements` (vacío) y describe los parámetros:

```java
@Operation(summary = "Horarios libres de un servicio (HU-20)", description = "Pública: no requiere sesión.")
@SecurityRequirements
@GetMapping
public ResponseEntity<ServiceAvailabilityResponse> get(
        @PathVariable UUID serviceId,
        @Parameter(description = "Fecha yyyy-MM-dd; sin ella, hoy (hora de Bogotá)", example = "2026-10-19")
        @RequestParam(required = false) String date) { ... }
```

Resultado verificado en `/v3/api-docs`: `POST /bookings` queda con etiqueta «Reservas», resumen, respuestas `201, 400, 404, 409` (con el esquema `ApiError`) **más** `401` y `403` añadidas por el paso 2; y `GET /services/{id}/availability` con `security: []` (sin candado).

**Patrones sin probar** (estándar de `swagger-annotations`, revisar al aplicarlos):
- Header de confirmación MFA en `PATCH /users/{id}/role` y `DELETE /users/{id}`: `@Parameter(in = ParameterIn.HEADER, name = "X-MFA-Code", description = "Código TOTP vigente del administrador", required = true)`.
- Ejemplos y descripciones por campo en los DTO: `@Schema(description = "Hora de inicio HH:mm", example = "10:00")` sobre cada campo.

### Paso 6 — Plantilla de decisión por endpoint (qué anotar según el caso)

| Caso del endpoint | Anotaciones |
|---|---|
| Cualquiera | `@Tag` en la clase; `@Operation(summary, description)` con la HU y el rol requerido |
| Devuelve `201` | `@ApiResponse(responseCode = "201", …)` (por defecto saldría `200`) |
| Tiene errores de negocio | un `@ApiResponse` por código con `ApiError`; los mensajes exactos están en [errores-api-sprint-2.md](errores-api-sprint-2.md) §7 |
| Es público | `@SecurityRequirements` |
| Recibe un header especial | `@Parameter(in = HEADER, …)` |
| Parámetros de consulta | `@Parameter(description, example)` |
| Es una confirmación (`CONFIRMATION_REQUIRED`) | `@ApiResponse(responseCode = "409")` explicando el campo `fields.affectedBookings` |

## 4. Cómo usar Swagger UI (para la demostración)

1. Abre `/swagger-ui.html` (local: `http://localhost:8080/swagger-ui.html`).
2. Ejecuta `POST /api/v1/auth/login` con un usuario de prueba y copia el `token` de la respuesta.
3. Botón **Authorize** (arriba a la derecha) → pega **solo el token** (sin la palabra `Bearer`) → *Authorize*. Desde ahí cada petición lleva `Authorization: Bearer …`.
4. Abre un endpoint → **Try it out** → edita el cuerpo → **Execute**: verás el código HTTP, el cuerpo y la cabecera.
5. Para un administrador, el login lleva `mfaCode` y las operaciones sensibles el header `X-MFA-Code` (parámetro propio de cada operación).

**Atajo:** `curl http://localhost:8080/v3/api-docs -o openapi.json` descarga la especificación; **Postman puede importar ese archivo** (Import → File) y crea una colección con todos los endpoints, útil para comparar con la colección propia de [`docs/postman/`](../postman/README.md).

## 5. Dónde tocar (alcance del cambio)

| Archivo | Cambio |
|---|---|
| `pom.xml` | +1 dependencia |
| `common/config/OpenApiConfig.java` | clase nueva (paso 2) |
| `identity/infrastructure/SecurityConfig.java` | +1 línea (paso 3) |
| `application.yml`, `application-dev.yml` | interruptor `springdoc.*` (paso 4) |
| 18 controladores (33 operaciones) | `@Tag` + `@Operation` + `@ApiResponses` (paso 5) |
| `src/test/…` | una prueba de integración que pida `/v3/api-docs` y compruebe que contiene los endpoints clave (evita que Swagger se rompa en silencio con una actualización) |

Esfuerzo estimado: ~1 día (la tarea `ARQ-01` del plan tiene 7 h). El cambio **no altera ningún comportamiento** de la API; las 614 pruebas existentes no deberían verse afectadas (no se corrieron con la dependencia agregada: el ensayo fue solo de arranque y de la especificación).

## 6. Trampas

- **`ResponseEntity<T>` no se infiere como `201`:** hay que declararlo con `@ApiResponse`.
- **`ApiError` aparece como esquema** solo si algún `@ApiResponse` lo referencia (es lo que se quiere).
- **Dos `ApiResponse` con el mismo nombre:** el de `io.swagger.v3.oas.annotations.responses` (anotación) y el de `io.swagger.v3.oas.models.responses` (modelo, en el customizer). Importa cada uno donde corresponda.
- **El plan gratuito de Render duerme el servicio:** si la demostración usa Swagger en Render, despiértalo antes (`/actuator/health`).
- **No dejes tokens reales** copiados en el navegador de un equipo compartido.
