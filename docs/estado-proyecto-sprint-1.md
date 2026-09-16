# Estado del proyecto — Sprint 1

Fecha de este corte: 2026-09-15. Alcance: `reservas-backend/` (único código existente en el repo). Basado en lectura directa del código fuente, los tests y la documentación en `docs/`.

## Resumen ejecutivo

El backend implementa **las seis historias de usuario de la Épica 0 — Identidad y Acceso (HU01–HU06) de punta a punta**: registro de cliente, inicio de sesión, registro de proveedor, cierre de sesión, gestión de roles y control de acceso transversal por rol/pertenencia. Todo lo demás del proyecto (servicios, recursos, reservas, reportes) sigue existiendo solo como "preparación arquitectónica" en la documentación, tal como estaba previsto para sprints posteriores.

HU01 se desarrolló primero como referencia; HU02–HU06 se agregaron después reutilizando deliberadamente lo que HU01 ya había dejado preparado (`JwtTokenProvider`, `Session`/`SessionRepository`, el catálogo de `AuditEventType`, los roles `PROVEEDOR`/`ADMINISTRADOR` ya sembrados).

Sigue sin resolverse el riesgo ya documentado antes: **el modelo de base de datos "formal" de Andraus (BD conceptual/lógica/física) y el esquema que Flyway realmente aplica siguen siendo dos diseños distintos** (UUID vs BIGSERIAL, nombres de columna distintos). Las migraciones nuevas (`V3`, `V4`, ver abajo) se hicieron siguiendo el estilo del esquema real (UUID), no el formal, por consistencia con V1/V2. Ver sección "Riesgos".

## 1. Qué está desarrollado

### Módulo `identity` (HU01, HU02, HU04, HU05 y el núcleo de HU06)

- **Registro de cliente** (HU01, sin cambios): `POST /api/v1/users`.
- **Inicio de sesión** (HU02): `POST /api/v1/auth/login` (`AuthController` → `AuthServiceImpl`). Verifica credenciales contra `UserRepository` + `PasswordEncoder`, exige un código MFA (TOTP) cuando el usuario tiene rol `ADMINISTRADOR` **y** ya activó su MFA (evita bloquear a un admin recién ascendido que aún no configuró nada), emite el JWT (`JwtTokenProvider`, ya existía) y persiste el control de sesión en `sessions` (`Session`/`SessionRepository`, ya existían). Un solo mensaje genérico (401) para credenciales incorrectas, cuenta deshabilitada o MFA inválida — no revela cuál falló.
- **Cierre de sesión** (HU04): `POST /api/v1/auth/logout` (autenticado). Revoca **todas** las sesiones activas del usuario (no solo la que hizo la petición — "todos los dispositivos asociados" según el Gherkin), vía `SessionRepository.revokeAllByUserId`.
- **MFA (TOTP, RFC 6238)**: implementado a mano con `javax.crypto` (sin librería externa) en `TotpService`. Endpoints propios (no estaban en `endpoints-sprint-1.md`, que da por hecho el mecanismo sin definir su contrato): `POST /api/v1/auth/mfa/setup` (idempotente, genera/devuelve el secreto pendiente) y `POST /api/v1/auth/mfa/activate` (confirma el código y activa). Nueva tabla `mfa` (`V4__create_mfa_table.sql`).
- **Autenticación real conectada**: `JwtAuthenticationFilter` (nuevo) puebla el `SecurityContext` a partir del header `Authorization: Bearer`, validando firma/expiración del JWT, que la sesión asociada (`jti`) siga vigente y no revocada, y que el usuario siga existiendo y habilitado. `IdentityService.getCurrentUser()` ya no devuelve siempre `Optional.empty()` — devuelve el usuario real cuando hay un token válido.
- **Gestión de roles y permisos** (HU05): `PATCH /api/v1/users/{userId}/role` y `DELETE /api/v1/users/{userId}` (ambos solo `ADMINISTRADOR`, vía `@PreAuthorize`), implementados en `UserManagementService`. Reglas: un usuario no puede modificar su propio rol ni eliminarse a sí mismo; el rol de un usuario `PROVEEDOR` es inmutable; un rol inexistente se rechaza con error uniforme; ascender a `ADMINISTRADOR` dispara la creación obligatoria (pendiente de activar) de la configuración MFA del usuario; `DELETE` solo admite usuarios `CLIENTE`/`PROVEEDOR` (no `ADMINISTRADOR`, interpretación del Gherkin — ver Riesgos).
- **Ejemplo de endpoint protegido de HU06**: `GET /api/v1/users/{userId}` — un Cliente/Proveedor solo puede consultarse a sí mismo; un Administrador puede consultar a cualquiera.
- **401/403 con formato uniforme**: `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` (nuevos, en `common.security`) reemplazan las respuestas por defecto de Spring Security por el mismo esquema `ApiError` que usa `GlobalExceptionHandler`, para las denegaciones que ocurren a nivel de filtro (antes de llegar a un controlador). Las denegaciones de `@PreAuthorize` (a nivel de método) las captura `GlobalExceptionHandler` directamente.
- **`AuthorizationService.hasPermission(...)` sigue sin implementar a propósito**: ninguna de las seis HU de este sprint requiere un catálogo de permisos granular (todas se resuelven con rol + pertenencia del recurso), así que se mantiene la misma decisión y el mismo test que ya existían.

### Módulo `provider` (nuevo — HU03)

- **Registro de proveedor**: `POST /api/v1/providers` (`ProviderController` → `ProviderRegistrationService`). Crea la cuenta de usuario con rol `PROVEEDOR` (vía `identity.application.UserProvisioningService`, el contrato nuevo que Identity expone para que otros módulos creen cuentas sin tocar `UserRepository` directamente — ADR-003), más las filas `providers` y `businesses` asociadas. Reutiliza el mismo `RegistrationRateLimiter` de HU01 (mismo contador por IP, para que no se pueda evadir el límite de HU01 alternando de endpoint).
- El DTO de entrada no tiene campo `role`: un cliente que intente enviar uno simplemente no tiene dónde aterrizar (mismo mecanismo que ya usaba HU01).
- **Demostración de HU06 sobre un recurso real de este sprint**: `GET /api/v1/providers/me` y `GET /api/v1/providers/{providerId}` — un proveedor puede consultar su propio negocio, pero recibe 403 si intenta consultar el de otro (no basta con tener el rol `PROVEEDOR` en general, se verifica pertenencia). No se simulan endpoints de Servicios/Recursos/Reservas solo para ejercitar la regla: esos módulos siguen sin código, como estaba previsto para sprints posteriores.
- Nuevas tablas `providers`/`businesses` (`V3__create_provider_schema.sql`), siguiendo el estilo UUID del esquema real (no el BIGSERIAL del modelo formal).

### Módulo `common` (ampliado)

- `common.validation`: `PasswordValidator`/`PhoneValidator`/`ValidPassword`/`ValidPhone` se movieron aquí desde `identity.controller.dto.validation` cuando `provider` empezó a necesitar las mismas reglas — evita duplicar la política de contraseña/celular en dos módulos.
- `common.security`: `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler` (ver arriba).
- `GlobalExceptionHandler` ahora también maneja 401 (`InvalidCredentialsException`), 404 (`UserNotFoundException`, `ProviderNotFoundException`) y 403 (`SelfModificationException`, `ProviderRoleImmutableException`, `AdminDeletionNotAllowedException`, `AccessDeniedException` de Spring Security), además de los que ya existían (400/409/429/500).

### Infraestructura y calidad

- **Migraciones Flyway nuevas**: `V3__create_provider_schema.sql` (`providers`, `businesses`), `V4__create_mfa_table.sql` (`mfa`).
- **Suite de pruebas ampliada**: 73 pruebas unitarias (Mockito + AssertJ, sin necesidad de base de datos): `AuthServiceImplTest`, `MfaServiceImplTest`, `UserManagementServiceImplTest`, `UserProvisioningServiceImplTest`, `ProviderRegistrationServiceTest`, `TotpServiceTest`, más las que ya existían de HU01. Se agregó también `AuthAndAccessControlIntegrationTest` (Testcontainers, mismo patrón que `UserRegistrationIntegrationTest`), cubriendo login/logout, cambio de rol, eliminación de usuario y pertenencia de proveedor de punta a punta.
- **Verificado de punta a punta contra la app real** (Docker + Postgres reales, `./mvnw spring-boot:run`, perfil `dev`), no solo con mocks: los seis HU se probaron manualmente endpoint por endpoint (registro, login con y sin MFA, control de acceso propio/ajeno, cambio de rol con todas sus reglas, ascenso a Administrador con disparo de MFA, logout con invalidación real de sesión, eliminación con cascada verificada en BD). En el proceso se encontró y corrigió un bug real que los 73 tests unitarios no detectaban (mockean el repositorio, así que nunca ejercitan el comportamiento real de Hibernate): `PATCH /api/v1/users/{id}/role` fallaba con 500 en su camino exitoso por usar un `Set` inmutable (`Set.of(...)`) sobre una entidad ya gestionada, que Hibernate necesita poder mutar al hacer `merge()`. Ver el detalle completo en [docs/resultados-pruebas-sprint-1.md](resultados-pruebas-sprint-1.md), sección "Verificación manual de punta a punta con Docker".
- `UserRegistrationIntegrationTest`/`AuthAndAccessControlIntegrationTest` (Testcontainers) siguen sin poder ejecutarse en esta máquina específica: Docker Desktop 4.90.0 responde con datos vacíos/mal formados al cliente `docker-java` sobre npipe (no es falta de Docker — `docker ps`/`spring-boot:run` contra el mismo Postgres funcionan perfectamente). Deberían correr sin cambios en WSL2, en un Docker Desktop más nuevo/antiguo, o en cualquier CI con Docker nativo de Linux.
- La app compila limpio (`./mvnw compile`, `./mvnw test-compile`) en este entorno con JDK 17.

## 2. Qué falta / riesgos abiertos

### No hay endpoint de bootstrap para el primer Administrador

Todas las rutas que requieren rol `ADMINISTRADOR` (cambio de rol, eliminación de usuario) solo se pueden invocar si ya existe al menos un usuario con ese rol — y no hay forma de crear el primero por HTTP (ningún registro público asigna `ADMINISTRADOR`, por diseño de HU01/HU03/HU05). En este sprint se resolvió únicamente para las pruebas de integración (fixture creado directamente por repositorio). Antes de un despliegue real hace falta decidir el mecanismo: semilla en una migración con contraseña forzada a rotar, comando administrativo fuera de la API, etc. — no es una decisión que corresponda tomar de forma unilateral en código.

### Dos modelos de base de datos sin conciliar (persiste de sprints anteriores)

Sigue abierto: el modelo "formal" de Andraus (BIGSERIAL, tablas `permissions`/`role_permissions`, columnas `phone`/`is_active`) y el esquema real de Flyway (UUID, sin esas tablas, `phone_number`/`enabled`) no se conciliaron. `V3`/`V4` (nuevas) siguen el esquema real por consistencia con V1/V2, y agregan `providers`/`businesses`/`mfa` con nombres de tabla que sí coinciden con el modelo lógico documentado, aunque no con sus tipos de PK.

### Interpretaciones tomadas donde el Gherkin/documento dejaba una decisión abierta

Ninguna de las seis HU llegó con una especificación 100% cerrada; estas son las decisiones tomadas y por qué, para que el equipo las confirme o las corrija:

- **`DELETE /api/v1/users/{userId}` solo admite `CLIENTE`/`PROVEEDOR`**, no `ADMINISTRADOR`: el escenario Gherkin de HU05 solo menciona esos dos roles explícitamente; se interpretó como una exclusión deliberada (evita que un admin se quede sin pares).
- **MFA**: HU02/HU05 exigen "verificación adicional"/"evento obligatorio de creación de MFA" sin definir el mecanismo. Se implementó TOTP (RFC 6238) completo, con endpoints propios no documentados en `endpoints-sprint-1.md`. Un Administrador recién ascendido puede iniciar sesión sin código hasta que complete `/mfa/setup` + `/mfa/activate` (evita un bloqueo permanente de la cuenta).
- **Logout revoca todas las sesiones del usuario**, no solo la que originó la petición — así lo pide el Gherkin de HU04 ("todos los dispositivos asociados"), aunque `endpoints-sprint-1.md` lo describe de forma más ambigua ("revocar la sesión correspondiente").
- **Cambio de rol reemplaza el rol del usuario** (no lo acumula): `User.roles` sigue siendo el mismo `Set<Role>` de HU01, así que cambiar de rol = reemplazar el contenido del set por el nuevo rol único, consistente con que HU01/HU03 siempre asignan exactamente un rol.

Estas decisiones están documentadas también como comentarios Javadoc en el código correspondiente (`UserManagementServiceImpl`, `MfaServiceImpl`, `AuthServiceImpl`).

### Transversal, en cualquier momento

- Confirmar el mecanismo de bootstrap del primer Administrador (ver arriba) antes de cualquier despliegue.
- Correr `AuthAndAccessControlIntegrationTest` y `UserRegistrationIntegrationTest` con Docker disponible para verificar de punta a punta lo que en este entorno solo se pudo compilar y razonar manualmente.
- Decidir si `ReservasBackendApplicationTests` migra a Testcontainers (pendiente de sprints anteriores).

## Referencias

- Especificación de endpoints: [docs/api/endpoints-sprint-1.md](api/endpoints-sprint-1.md) y [docs/api/dtos-sprint-1.md](api/dtos-sprint-1.md)
- Arquitectura: [docs/arquitectura/arquitectura-sprint-1.md](arquitectura/arquitectura-sprint-1.md) y ADRs en `docs/arquitectura/adr/`
- Historias de usuario: `docs/HU-01-Registrar-cliente.txt`, `docs/HU 02 - Inicio de sesión.txt`, `docs/HU - 03 Registro de proveedor de se.txt`, `docs/HU 04 - Cerrar sesión.txt`, `docs/HU 05 - Gestionar roles y permisos.txt`, `docs/HU 06 - Acceso segun rol.txt`
- Modelo de BD formal (Andraus): `docs/bd/modelo/01_modelo_conceptual.md`, `02_modelo_logico.md`, `03_modelo_fisico.md`
