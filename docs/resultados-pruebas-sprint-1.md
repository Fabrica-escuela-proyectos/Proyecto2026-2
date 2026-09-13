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

1. **Instalar Docker Desktop** (o Docker Engine vía WSL2) y dejarlo corriendo. Esto resuelve directamente `UserRegistrationIntegrationTest`, que ya está correctamente escrita para usarlo.
2. **Para `ReservasBackendApplicationTests`**, evaluar una de estas dos opciones (es una decisión de diseño, no la tomé por mi cuenta):
   - Levantar un Postgres local (o vía Docker) accesible en `localhost:5432` con las credenciales por defecto (`reservas_app` / sin password) y las migraciones de Flyway aplicadas, **o**
   - Ajustar el test para que use el perfil `test` + Testcontainers igual que `UserRegistrationIntegrationTest` (hoy no lo hace; corre contra el perfil `dev` real).
3. Con Docker disponible, volver a correr `./mvnw test` y confirmar que los 29 tests pasan (`Tests run: 29, Failures: 0, Errors: 0`).
4. Una vez verificado, commitear el fix de compilación (`pom.xml` + `UserRegistrationIntegrationTest.java`) que sigue sin commitear en `main`.
