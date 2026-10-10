# Swagger / OpenAPI: cómo está aplicado y cómo mantenerlo

**Estado: aplicado el 2026-10-10** (tareas `ARQ-01`, `API-01`, `API-02`). Verificado con la suite completa (`OpenApiDocumentationIntegrationTest`, `OpenApiDisabledIntegrationTest`) y abriendo Swagger UI en una instancia local (carga, grupos, candados, cuerpo de ejemplo y respuestas de una operación). **No se ejercitó el botón *Authorize* ni *Execute* de la interfaz** (la especificación sí declara el esquema Bearer y se comprobó por prueba) y **no está verificado en Render** (el cambio aún no se ha desplegado con `SWAGGER_ENABLED=true`).

## 1. Qué se obtiene

- **OpenAPI 3.1** generado del código en `/v3/api-docs` y **Swagger UI** en `/swagger-ui.html`, donde cada endpoint muestra su resumen (con la HU), su rol, sus parámetros con ejemplos, sus cuerpos con ejemplos y **todas** sus respuestas (códigos, descripciones y el formato `ApiError` de los errores). Se puede probar en vivo con un token.
- 8 grupos: *Autenticación y MFA, Usuarios, Proveedores y negocio, Catálogo y disponibilidad, Servicios, Recursos, Reservas del cliente, Reservas del proveedor* (33 operaciones, 47 esquemas).
- Complementa, no reemplaza: [`referencia-api-sprint-2.md`](referencia-api-sprint-2.md) (matriz de acceso y DTO, generada del código), [`errores-api-sprint-2.md`](errores-api-sprint-2.md) (catálogo de errores) y [`endpoints-sprint-2.md`](endpoints-sprint-2.md) (reglas de negocio narradas).

## 2. Cómo usarla

| Entorno | Cómo se abre |
|---|---|
| **Local** (perfil `dev`, el de `./mvnw spring-boot:run`) | `http://localhost:8080/swagger-ui.html`. Ya viene encendido. |
| **Render** (perfil `prod`) | Está **apagado**. Para una demostración: en el panel de Render agrega la variable `SWAGGER_ENABLED=true`, espera el redeploy y abre `https://proyecto2026-2-5zoo.onrender.com/swagger-ui.html`. **Al terminar, elimina la variable** (la documentación expone la lista completa de rutas; OWASP A05). Antes de la demo despierta el servicio con `/actuator/health` (el plan gratuito lo duerme a los 15 min). |
| Descargar la especificación | `curl http://localhost:8080/v3/api-docs -o openapi.json`. **Postman** puede importarla (Import → File) y crea una colección con todos los endpoints. |

Para llamar endpoints protegidos desde la interfaz:

1. Abre `POST /api/v1/auth/login` → **Try it out** → envía `email`, `password` (y `mfaCode` si la cuenta tiene MFA) → copia el `token` de la respuesta.
2. Botón **Authorize** (arriba a la derecha) → pega **solo el token** (sin `Bearer`) → *Authorize*. Desde ahí cada petición lleva `Authorization: Bearer …`.
3. Abre el endpoint → **Try it out** → edita el cuerpo (ya trae ejemplos) → **Execute**.
4. Un administrador necesita además el header `X-MFA-Code` (aparece como parámetro obligatorio de `PATCH /users/{id}/role` y `DELETE /users/{id}`).
5. Los candados indican qué rutas piden token; las públicas (`login`, registro de cliente y de proveedor, disponibilidad de un servicio) **no tienen candado**.

No dejes un token real en el navegador de un equipo compartido.

## 3. Qué se cambió en el repositorio

| Archivo | Cambio |
|---|---|
| `pom.xml` | `springdoc-openapi-starter-webmvc-ui` **3.1.1** (funciona con Spring Boot 4.1.1 y Jackson 3) |
| `common/config/OpenApiConfig.java` (nuevo) | Título, esquema **Bearer JWT** (botón Authorize, requisito global) y un `OpenApiCustomizer` que corrige la especificación ya generada (ver §5) |
| `common/config/OpenApiTags.java` (nuevo) | Nombres y orden de los 8 grupos; las constantes se usan en `@Tag` |
| `identity/infrastructure/SecurityConfig.java` | `permitAll` para `/v3/api-docs/**`, `/swagger-ui/**` y `/swagger-ui.html` (si el interruptor está apagado esas rutas no existen y responden `404`) |
| `application.yml` | `springdoc.api-docs.enabled` y `springdoc.swagger-ui.enabled` = `${SWAGGER_ENABLED:false}` → **apagado por defecto** |
| `application-dev.yml`, `application-test.yml` | Encendido (dev para trabajar; test para las pruebas) |
| 18 controladores | `@Tag`, y por operación `@Operation` (resumen con la HU, reglas y rol), un `@ApiResponse` por código, `@SecurityRequirements` en las públicas, `@Parameter` en los parámetros y header `X-MFA-Code` |
| DTO de entrada (11 archivos) | `@Schema(description, example)` en los campos. **Sin ejemplos de contraseñas** (el repositorio es público) |
| `OpenApiDocumentationIntegrationTest`, `OpenApiDisabledIntegrationTest` (nuevos) | 8 pruebas; ver §6 |

El cambio no altera ningún comportamiento de la API: salvo las 8 pruebas nuevas, la suite no cambió (622 pruebas, 0 fallos al 2026-10-10).

## 4. Cómo documentar un endpoint nuevo

Cada endpoint nuevo **debe** llevar esto, o la prueba `todoEndpointDeLaAplicacionEstaDocumentadoConResumenYGrupo` falla y dice cuál falta:

```java
@RestController
@Tag(name = OpenApiTags.CLIENT_BOOKINGS)                  // 1. grupo (constante de OpenApiTags)
public class BookingController {

    @Operation(summary = "Crear una reserva (HU-22)",      // 2. resumen con la HU + descripción con reglas y rol
            description = "Solo CLIENTE. …")
    @ApiResponse(responseCode = "201", description = "Reserva CONFIRMADA")        // 3. un @ApiResponse por código…
    @ApiResponse(responseCode = "400", description = "Fecha, horas, duración…")   //    …solo código y texto:
    @ApiResponse(responseCode = "409", description = "Horario ocupado…")          //    el cuerpo de error lo pone el customizer
    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<BookingResponse> create(...) { ... }
}
```

| Caso | Qué añadir |
|---|---|
| Devuelve `201` o `204` | `@ApiResponse` con ese código (si no, Swagger muestra `200`) |
| Ruta **pública** (sin token) | `@SecurityRequirements` (vacío) y un `permitAll` en `SecurityConfig` |
| Header o parámetro especial | `@Parameter(name = "X-MFA-Code", in = ParameterIn.HEADER, …)` a nivel de método, o `@Parameter(description, example)` delante del argumento |
| Campo de un DTO de entrada | `@Schema(description = "…", example = "…")`. Las validaciones (`@Size`, `@Pattern`, `@Min`…) ya salen solas |
| Grupo nuevo | Constante y entrada en `OpenApiTags` |
| 401 / 403 | **No hace falta**: el customizer los añade a toda ruta protegida; solo se declaran para dar un texto más específico (p. ej. `MFA_REQUIRED`) |

Los mensajes de error exactos por HU están en [`errores-api-sprint-2.md`](errores-api-sprint-2.md) §7.

## 5. Lo que descubrimos al aplicarla (por qué existe el customizer)

springdoc por sí solo hace menos de lo que parece. Lo que se vio con la interfaz abierta y con la especificación volcada:

| Comportamiento de springdoc | Corrección en `OpenApiConfig` |
|---|---|
| **Copia el esquema de la respuesta de éxito a todas las respuestas declaradas**: un `409` de `POST /bookings` salía describiendo `BookingResponse`, lo cual es falso | Toda respuesta `4xx/5xx` se reemplaza por `ApiError` (`application/json`) |
| Las respuestas de éxito salen como `*/*` | Se cambian a `application/json` |
| No sabe que 401 y 403 existen en toda ruta protegida (salen de Spring Security, no del controlador) | Se añaden a toda operación que no sea pública (sin pisar el texto que el controlador ya dio) |
| Solo documenta `200` si no se anota | `@ApiResponse` por código en cada operación |
| Etiquetas automáticas (`booking-controller`…) | `@Tag` con `OpenApiTags` (nombre legible y orden fijo) |
| El esquema `ApiError` no aparece si nadie lo referencia | Se registra explícitamente |

Trampas conocidas:

- **`ApiResponse` tiene dos clases:** `io.swagger.v3.oas.annotations.responses.ApiResponse` (anotación, en los controladores) y `io.swagger.v3.oas.models.responses.ApiResponse` (modelo, en el customizer). Importa cada una donde corresponde.
- **El header `X-MFA-Code` figura como obligatorio** en la interfaz aunque el servidor lo trate como opcional (responde `401 MFA_REQUIRED` si falta): así el botón *Execute* no se envía sin él. Para probar el caso «falta el código» usa Postman o `curl`.
- **Con el perfil por defecto (`dev`) Swagger queda encendido.** En Render el perfil es `prod`; si algún despliegue olvidara `SPRING_PROFILES_ACTIVE` quedaría abierto (parte del hallazgo `SEC-05` de [OWASP](../seguridad/owasp-top10-sprint-2.md)).
- **Springdoc 3.1.1 con Spring Boot 4.1:** si una actualización de Spring Boot rompe la generación, las pruebas de `OpenApiDocumentationIntegrationTest` fallan en el pipeline en vez de enterarse en la demostración.

## 6. Qué comprueban las pruebas

| Prueba | Garantiza |
|---|---|
| `laEspecificacionEsOpenApi31…` | La especificación existe, trae el esquema Bearer y `ApiError`, y los ejemplos y validaciones de los DTO |
| `todoEndpointDeLaAplicacionEstaDocumentado…` | **Cada** endpoint implementado en la aplicación (leído de `RequestMappingHandlerMapping`) aparece en la especificación con resumen y grupo: un endpoint nuevo sin anotar rompe el build |
| `soloLasRutasPublicasQuitanElCandado…` | Las 4 rutas públicas van sin candado; las demás exigen token y declaran 401 y 403; ningún error describe el tipo de la respuesta de éxito |
| `lasRespuestasDeErrorDeclaradas…` | `POST /bookings` declara 201/400/404/409/401/403 con `ApiError` |
| `elHeaderDeConfirmacionMfa…` | `X-MFA-Code` aparece una sola vez en `PATCH …/role` y `DELETE /users/{id}` |
| `swaggerUiSeSirveSinToken…` | La interfaz se sirve sin token cuando está encendida |
| `OpenApiDisabledIntegrationTest` | Con el interruptor apagado, `/v3/api-docs`, `/swagger-ui/index.html` y `/swagger-ui.html` responden `404` y la API normal sigue funcionando |

## 7. Pendiente

- Verificar en Render tras el despliegue: con `SWAGGER_ENABLED` sin definir debe dar `404`; con `true`, la interfaz carga y funciona *Authorize*.
- Ejemplos de las respuestas (hoy los ejemplos están solo en los cuerpos de entrada y en los parámetros; las respuestas muestran el esquema).
- Decidir con el equipo si en la sustentación se enciende en Render o se muestra en local ([decisiones](../arquitectura/decisiones-y-supuestos-sprint-2.md), S-30).
