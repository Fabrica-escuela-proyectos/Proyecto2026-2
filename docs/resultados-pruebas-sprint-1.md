# Resultados de pruebas — Sprint 1

## Contexto de la ejecución

- **Fecha:** 2026-09-13
- **Comando:** `./mvnw test`
- **JDK usado:** Microsoft Build of OpenJDK 17.0.20.101 (`JAVA_HOME` apuntando a `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`)
- **Docker:** no disponible en la máquina donde se ejecutó esta corrida
- **Working tree:** con el fix de compilación aplicado (dependencia `spring-boot-webmvc-test` en `pom.xml` + import corregido de `AutoConfigureMockMvc` en `UserRegistrationIntegrationTest`), aún sin commitear en `main`

## Resumen

| Clase | Tests | Passed | Failed | Errors |
|---|---|---|---|---|
| `ReservasBackendApplicationTests` | 1 | 0 | 0 | 1 |
| `AuthorizationServiceImplTest` | 4 | 4 | 0 | 0 |
| `UserRegistrationServiceTest` | 6 | 6 | 0 | 0 |
| `UserRegistrationIntegrationTest` | 1* | 0 | 0 | 1 |
| `PasswordValidatorTest` | 8 | 8 | 0 | 0 |
| `PhoneValidatorTest` | 5 | 5 | 0 | 0 |
| `JwtTokenProviderTest` | 4 | 4 | 0 | 0 |
| **Total** | **29** | **27** | **0** | **2** |

\* La clase tiene 6 `@Test` definidos, pero JUnit solo reporta 1 porque el error ocurre al levantar el contexto/Testcontainers, antes de ejecutar los métodos individuales.

**Ningún test falló por lógica de negocio incorrecta.** Los 27 tests unitarios (validadores, `UserRegistrationService`, `AuthorizationServiceImpl`, `JwtTokenProvider`) pasan limpio. Los 2 errores son de infraestructura de entorno, no de código.

## Detalle de los 2 errores

### 1. `ReservasBackendApplicationTests.contextLoads`

```
Unable to determine Dialect without JDBC metadata (please set 'jakarta.persistence.jdbc.url' ...)
```

**Causa:** este test hace `@SpringBootTest` sin perfil activo, así que toma el perfil `dev` por defecto (`application.yml`), que apunta a `jdbc:postgresql://localhost:5432/reservas`. No hay Postgres real escuchando en ese puerto en esta máquina, así que Hibernate no logra conectarse ni determinar el dialecto SQL.

A diferencia de `UserRegistrationIntegrationTest`, esta clase **no** usa Testcontainers ni `@DynamicPropertySource` — depende de una base de datos real ya levantada.

### 2. `UserRegistrationIntegrationTest`

```
IllegalState: Could not find a valid Docker environment. Please see logs and check configuration
```

**Causa:** esta clase usa `@Testcontainers` para levantar un contenedor Postgres real (ver su propio javadoc, que ya documentaba esto como pendiente). Sin Docker instalado/corriendo, Testcontainers no tiene dónde levantar el contenedor.

## Pasos a seguir

1. **Instalar Docker Desktop** (o Docker Engine vía WSL2) y dejarlo corriendo. Esto resuelve directamente `UserRegistrationIntegrationTest`, que ya está correctamente escrita para usarlo. → **Hecho**, ver actualización abajo.
2. **Para `ReservasBackendApplicationTests`**, evaluar una de estas dos opciones (es una decisión de diseño, no la tomé por mi cuenta):
   - Levantar un Postgres local (o vía Docker) accesible en `localhost:5432` con las credenciales por defecto (`reservas_app` / sin password) y las migraciones de Flyway aplicadas, **o**
   - Ajustar el test para que use el perfil `test` + Testcontainers igual que `UserRegistrationIntegrationTest` (hoy no lo hace; corre contra el perfil `dev` real).
3. Con Docker disponible, volver a correr `./mvnw test` y confirmar que los 29 tests pasan (`Tests run: 29, Failures: 0, Errors: 0`).
4. Una vez verificado, commitear los fixes pendientes (ver lista al final).

## Actualización — probando `./mvnw spring-boot:run` con Docker

Con Docker Desktop instalado y un Postgres de desarrollo levantado vía `docker-compose.yml` (nuevo, en `reservas-backend/`), aparecieron dos problemas más al arrancar la app real. Ninguno es lógica de negocio; ambos son configuración/infraestructura:

### 3. `FlywayAutoConfiguration` movida en Spring Boot 4.1.1

```
Schema validation: missing table [audit_logs]
```

Con Postgres en Docker conectando bien, Hibernate seguía fallando la validación de esquema porque Flyway **nunca se ejecutaba** (no había ni rastro de sus logs, ni la tabla `flyway_schema_history`). Causa: igual que con `AutoConfigureMockMvc` (ver "Correccion import"), en Spring Boot 4.1.1 `FlywayAutoConfiguration` se separó de `spring-boot-autoconfigure` a un artefacto propio, `spring-boot-flyway`, que no se agrega automáticamente solo por tener `flyway-core` + `flyway-database-postgresql` en el classpath.

**Fix:** agregar la dependencia `org.springframework.boot:spring-boot-flyway` en `pom.xml`.

### 4. Migraciones en la carpeta equivocada

Con `spring-boot-flyway` ya agregado, Flyway corría pero reportaba `No migrations found. Are your locations set up correctly?`. Causa: `application.yml` apunta a `spring.flyway.locations: classpath:db/migration`, pero los scripts (`V1__create_identity_schema.sql`, `V2__create_sessions_table.sql`) estaban en `src/main/resources/db/`, un nivel arriba de la carpeta `migration/` (que existía vacía).

**Fix:** mover ambos scripts a `src/main/resources/db/migration/` (con `git mv`, preservando historial).

### Resultado final

Con los 4 fixes aplicados (import de MockMvc + `spring-boot-flyway` + ubicación de migraciones + Postgres en Docker), `./mvnw spring-boot:run` migra el esquema completo y arranca:

```
Migrating schema "public" to version "1 - create identity schema"
Migrating schema "public" to version "2 - create sessions table"
Successfully applied 2 migrations to schema "public", now at version v2
Tomcat started on port 8080 (http) with context path '/'
Started ReservasBackendApplication in 4.308 seconds
```

Verificado con `psql` dentro del contenedor: las 6 tablas existen (`audit_logs`, `flyway_schema_history`, `roles`, `sessions`, `user_roles`, `users`).

### Cambios pendientes de commitear en `main`

- `reservas-backend/pom.xml`: dependencia `spring-boot-flyway` (runtime)
- `reservas-backend/src/main/resources/db/migration/V1__create_identity_schema.sql` y `V2__create_sessions_table.sql` (movidos desde `src/main/resources/db/`)
- `reservas-backend/docker-compose.yml` (nuevo): Postgres de desarrollo para el proyecto, usuario `reservas_app` / base `reservas` / password `reservas_app_dev`

### Pendiente de decisión de equipo

`ReservasBackendApplicationTests` (punto 2 arriba) sigue sin resolver: no usa Testcontainers, requiere que `localhost:5432` tenga la base `reservas` accesible con las credenciales del perfil activo. Con el Postgres de Docker ya levantado y `DB_PASSWORD` exportado, este test debería pasar; falta confirmarlo corriendo `./mvnw test` con el contenedor arriba.

## Actualización — `./mvnw test` con Postgres (Docker) arriba

Con el contenedor `reservas-postgres` corriendo (`docker compose up -d`) y `DB_PASSWORD=reservas_app_dev` exportado, se volvió a correr la suite completa.

### Resumen actualizado

| Clase | Tests | Passed | Failed | Errors |
|---|---|---|---|---|
| `ReservasBackendApplicationTests` | 1 | 1 | 0 | 0 |
| `AuthorizationServiceImplTest` | 4 | 4 | 0 | 0 |
| `UserRegistrationServiceTest` | 6 | 6 | 0 | 0 |
| `UserRegistrationIntegrationTest` | 1* | 0 | 0 | 1 |
| `PasswordValidatorTest` | 8 | 8 | 0 | 0 |
| `PhoneValidatorTest` | 5 | 5 | 0 | 0 |
| `JwtTokenProviderTest` | 4 | 4 | 0 | 0 |
| **Total** | **29** | **28** | **0** | **1** |

**`ReservasBackendApplicationTests` ya pasa** — confirma que el punto 2 de "Pasos a seguir" quedó resuelto simplemente con el Postgres de Docker ya levantado; no hizo falta tocar el test.

### 5. `UserRegistrationIntegrationTest` — Testcontainers no encuentra el Docker de Windows (causa distinta a la original)

Este test sigue fallando, pero **ya no por falta de Docker** — ahora es un problema de cómo Testcontainers se conecta al Docker Desktop de Windows desde este entorno:

```
NpipeSocketClientProviderStrategy: failed with exception BadRequestException
(Status 400: {... "Labels":["com.docker.desktop.address=npipe://\\\\.\\pipe\\docker_cli"] ...})
```

**Causa:** Testcontainers intenta hablar con Docker vía el pipe de Windows `npipe:////./pipe/docker_engine`, pero la respuesta que recibe viene vacía/inconsistente (0 contenedores, sin versión de kernel, etc.) y con una etiqueta que apunta a `docker_cli` en vez de al engine real. `docker ps` desde la terminal sí funciona perfectamente en esta misma máquina — el CLI y Testcontainers están resolviendo el daemon por rutas distintas. Probar `DOCKER_HOST=npipe:////./pipe/docker_engine` explícito no cambió el resultado (es el mismo valor que Testcontainers ya intenta por defecto).

**Próximo paso sugerido** (no lo apliqué porque requiere cambiar configuración de Docker Desktop, que no controlo desde aquí):
1. En Docker Desktop → *Settings → General*, activar **"Expose daemon on tcp://localhost:2375 without TLS"**.
2. Exportar `DOCKER_HOST=tcp://localhost:2375` antes de correr `./mvnw test`.
3. Alternativa: correr las pruebas desde dentro de WSL2 (si se configura), donde Testcontainers habla con Docker vía socket Unix nativo en vez del pipe de Windows, evitando este problema por completo.

### Cambios pendientes de commitear en `main` (actualizado)

Sin cambios adicionales de código en esta ronda — el `pom.xml`, las migraciones movidas y el `docker-compose.yml` ya fueron commiteados (`Integracion Postgres y Docker para pruebas`, `Agregar spring-boot-flyway...`). El working tree está limpio.

## Actualización — 2026-09-15, implementación de HU02–HU06

- **Comando:** `./mvnw test -Dtest='!ReservasBackendApplicationTests,!UserRegistrationIntegrationTest,!AuthAndAccessControlIntegrationTest'`
- **Docker:** no disponible en esta máquina/sesión (`docker version` conecta al cliente pero falla al hablar con el daemon: `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`) — un fallo distinto y más básico que el de la actualización anterior (ahí Docker sí respondía, solo enrutaba mal); aquí el daemon de Docker Desktop directamente no está corriendo. Se intentó localizar y arrancar `Docker Desktop.exe` sin éxito (no estaba en la ruta por defecto).

### Resumen

| Clase | Tests | Passed | Failed | Errors |
|---|---|---|---|---|
| Las 7 clases ya listadas arriba (sin cambios) | 29 | — | — | — |
| `AuthServiceImplTest` (nueva, HU02/HU04) | 8 | 8 | 0 | 0 |
| `MfaServiceImplTest` (nueva, HU02/HU05) | 9 | 9 | 0 | 0 |
| `UserManagementServiceImplTest` (nueva, HU05/HU06) | 14 | 14 | 0 | 0 |
| `UserProvisioningServiceImplTest` (nueva, HU03) | 4 | 4 | 0 | 0 |
| `ProviderRegistrationServiceTest` (nueva, HU03) | 3 | 3 | 0 | 0 |
| `TotpServiceTest` (nueva) | 8 | 8 | 0 | 0 |
| `PasswordValidatorTest`/`PhoneValidatorTest` (movidas a `common.validation`, mismo contenido) | 13 | 13 | 0 | 0 |
| **Total (excluyendo las 3 clases que requieren Postgres/Docker real)** | **73** | **73** | **0** | **0** |

`./mvnw compile` y `./mvnw test-compile` también se corrieron limpio sobre todo el árbol (identity, provider, common, audit).

No se pudieron ejecutar en este entorno (mismo motivo de siempre: sin Docker): `ReservasBackendApplicationTests`, `UserRegistrationIntegrationTest` (ninguna de las dos es nueva) y `AuthAndAccessControlIntegrationTest` (nueva — Testcontainers, mismo patrón que `UserRegistrationIntegrationTest`, cubre login/logout/cambio de rol/eliminación/pertenencia de proveedor de punta a punta incluyendo el filtro JWT real). Quedan listas para correr en cualquier entorno con Docker funcional.

## Actualización — 2026-09-15 (mismo día), verificación manual de punta a punta con Docker

Con Docker Desktop ya corriendo y el contenedor `reservas-postgres` (`docker-compose.yml`) arriba, se repitió la corrida completa (`DB_PASSWORD=reservas_app_dev ./mvnw test`, sin exclusiones).

- **`ReservasBackendApplicationTests` ahora pasa** contra el contenedor real, y confirma que `V3__create_provider_schema.sql`/`V4__create_mfa_table.sql` migran limpio sobre el esquema V1/V2 ya existente (`Current version: 2` → `Migrating... 3` → `Migrating... 4`).
- **`UserRegistrationIntegrationTest` y `AuthAndAccessControlIntegrationTest` siguen sin poder correr**, pero ya no por falta de Docker: es el mismo problema de compatibilidad Testcontainers↔Docker Desktop en Windows descrito en la sección "5." de arriba (`NpipeSocketClientProviderStrategy` recibe una respuesta vacía/mal formada del daemon). Se probó explícitamente `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine` (el contexto activo real, según `docker context ls`) y falla igual — no es un problema de qué pipe se usa, sino de cómo el cliente `docker-java` que trae Testcontainers habla con esta versión de Docker Desktop (4.90.0) sobre npipe. `docker ps`/`docker version` desde la CLI funcionan perfectamente en la misma máquina. Exponer el daemon en `tcp://localhost:2375` (sugerido en la actualización anterior) seguía sin estar habilitado; requiere un cambio manual en Docker Desktop → Settings que no se aplicó por no ser una decisión unilateral de tomar en código.
- **Como alternativa, se verificó cada endpoint manualmente** contra la app real (`./mvnw spring-boot:run`, perfil `dev`, mismo contenedor Postgres) con `curl`, cubriendo los seis HU de punta a punta: registro de cliente (éxito + 5 rechazos), login (éxito + credenciales inválidas, con y sin MFA), acceso protegido propio/ajeno (200/403/401 con token manipulado), registro de proveedor (éxito, intento de auto-asignar rol admin ignorado, correo duplicado, campo faltante), pertenencia de negocio de proveedor (200/403/404), cambio de rol (éxito, rol de Proveedor inmutable, auto-modificación bloqueada, rol inexistente, usuario inexistente, llamador no-admin), ascenso a Administrador con disparo del evento MFA pendiente, enrolamiento y activación de MFA (el código TOTP se calculó de forma independiente con un script Python propio — no reutilizando la lógica Java bajo prueba — para descartar que ambas implementaciones compartieran el mismo error), logout con invalidación real del token, y eliminación de usuario (bloqueo de auto-eliminación y de eliminar Administradores, éxito sobre Proveedor con cascada verificada en BD hacia `providers`/`businesses`).

### Bug real encontrado y corregido

`PATCH /api/v1/users/{userId}/role` devolvía **500** en el camino exitoso (HTTP real; los 73 tests unitarios con Mockito no lo detectaban porque mockean `UserRepository` y nunca ejercitan el comportamiento real de Hibernate).

- **Causa:** `UserManagementServiceImpl.changeRole` hacía `target.setRoles(Set.of(newRole))` sobre una entidad ya gestionada (cargada con `findById`). `JpaRepository.save(...)` sobre una entidad gestionada pasa por `EntityManager.merge()`, y el algoritmo de merge de colecciones de Hibernate necesita hacer `clear()` sobre el valor asignado para reconciliarlo con la colección persistente — algo que un `Set.of()` inmutable no permite (`UnsupportedOperationException`). HU01/HU03 usan el mismo patrón (`.roles(Set.of(...))`) pero sobre una entidad **nueva** (`id` nulo), que pasa por `persist()`, no por `merge()`, así que nunca lo manifestaron.
- **Fix:** `target.setRoles(new HashSet<>(Set.of(newRole)))` (colección mutable). Verificado corrigiendo el bug, recompilando, y repitiendo la secuencia completa de HU05 contra la app real — todos los casos (éxito, inmutabilidad de Proveedor, auto-modificación, rol inexistente, usuario inexistente, ascenso a Administrador con evento MFA) pasan.
- **Hallazgo secundario relacionado:** `GlobalExceptionHandler.handleUnexpected` no logueaba la excepción capturada — un 500 real en producción habría sido indiagnosticable server-side (el cliente correctamente nunca ve el detalle, pero nada quedaba registrado tampoco). Se agregó `log.error(...)` con el stack trace completo antes de construir la respuesta genérica; así se encontró la causa raíz de este mismo bug.

### Resultado final de esta ronda

- 74 tests automatizados (73 unitarios + `ReservasBackendApplicationTests`) pasan limpio contra el contenedor Postgres real.
- Los 6 HU quedaron verificados manualmente de punta a punta contra la app real, con un bug real encontrado y corregido en el proceso.
- Pendiente: correr `UserRegistrationIntegrationTest` y `AuthAndAccessControlIntegrationTest` en un entorno donde Testcontainers sí pueda hablar con Docker (WSL2, Docker Desktop con el daemon expuesto por TCP, o CI con Docker nativo de Linux) — el código de ambas pruebas no cambió, el bloqueo es puramente de esta máquina/versión de Docker Desktop.
