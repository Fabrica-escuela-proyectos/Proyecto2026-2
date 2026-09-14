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
