# Estado del proyecto — Sprint 1

Fecha de este corte: 2026-09-15, actualizado 2026-09-22. Alcance: `reservas-backend/` (único código existente en el repo). Basado en lectura directa del código fuente, los tests y la documentación en `docs/`.

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

### Bootstrap del primer Administrador — resuelto

Ya no es un riesgo abierto: `identity/application/AdminBootstrapRunner.java` crea el primer `ADMINISTRADOR` al arrancar la app, leyendo `BOOTSTRAP_ADMIN_EMAIL`/`BOOTSTRAP_ADMIN_PASSWORD`/`BOOTSTRAP_ADMIN_CELLPHONE` (y opcionalmente `BOOTSTRAP_ADMIN_FULL_NAME`) del entorno. No expone ninguna ruta HTTP, no hace nada si ya existe un Administrador (reiniciar no duplica), y valida los datos con las mismas reglas de contraseña/celular del resto de la app. Ver `.env.example` y [docs/guia-despliegue-render.md](guia-despliegue-render.md).

### Verificación detallada de criterios de aceptación (2026-09-22)

Se hizo un cruce escenario por escenario de los 6 Gherkin (`docs/HU-*.txt`) contra el código — no solo "¿existe el endpoint?" sino cada condición puntual de cada `Then`. Resultado: **34 de 39 criterios implementados, 2 no implementados, 3 parciales.** Detalle completo con archivo:línea en [docs/verificacion-criterios-aceptacion-sprint-1.md](verificacion-criterios-aceptacion-sprint-1.md). Los 5 puntos que no cumplen el Gherkin al 100%:

1. **No existe endpoint para cambiar la propia contraseña** (HU-02) — funcionalidad ausente, no solo sin el paso de confirmación adicional que pide el escenario.
2. **`PATCH /users/{id}/role` no exige confirmación adicional** antes de ejecutar el cambio (mismo escenario de HU-02, rama "modificar permisos de otro usuario").
3. **El registro (HU-01) no deja al usuario autenticado** — cumple la rama "o redirige a login" del criterio (que es un OR), pero no está señalado como decisión intencional en el código.
4. **Doble logout / logout sin sesión dan el mismo 401 genérico** (HU-04) — no hay un mensaje distinto para "la sesión ya estaba cerrada".
5. **El mensaje de "no puede modificarse a sí mismo" (HU-05) solo se ve si quien lo intenta ya es Administrador** — un Cliente/Proveedor que lo intenta ve el mensaje genérico de rol insuficiente antes de llegar a esa validación, aunque el resultado final (rechazado) es correcto igual.

### Dos modelos de base de datos sin conciliar — análisis completo (2026-09-22)

Se hizo la comparación tabla por tabla entre el modelo formal de Andraus y el esquema real de Flyway (`V1`–`V4`). Detalle completo en [docs/conciliacion-modelo-bd-sprint-1.md](conciliacion-modelo-bd-sprint-1.md). Resumen:

- La diferencia de fondo (UUID vs BIGSERIAL) no se recomienda resolver migrando la app real — ya está probada y desplegada contra UUID. Se recomienda en cambio actualizar los documentos del modelo formal para que describan el esquema real.
- **Hallazgo importante:** la política `ON DELETE RESTRICT` del modelo formal, aplicada literalmente, haría imposible cumplir el `DELETE` de usuario de HU-05 (ya implementado y verificado) — la mayoría de usuarios tendría historial de auditoría/sesión y la BD rechazaría el borrado. El esquema real evita esto con `ON DELETE CASCADE` y sin FK en `audit_logs`.
- Seed de rol `ADMIN` (modelo formal) vs `ADMINISTRADOR` (código real, usado en `@PreAuthorize`) — si el script formal se ejecutara tal cual, ninguna verificación de rol de administrador funcionaría.
- El modelo formal sí tiene ideas mejores que vale la pena adoptar sin urgencia: historial de cambios de rol en `user_roles`, y un `CHECK` de duración máxima de sesión a nivel de BD.

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

- Verificación criterio por criterio de las 6 HU: [docs/verificacion-criterios-aceptacion-sprint-1.md](verificacion-criterios-aceptacion-sprint-1.md)
- Conciliación del modelo de base de datos: [docs/conciliacion-modelo-bd-sprint-1.md](conciliacion-modelo-bd-sprint-1.md)
- Guía de despliegue: [docs/guia-despliegue-render.md](guia-despliegue-render.md)
- Especificación de endpoints: [docs/api/endpoints-sprint-1.md](api/endpoints-sprint-1.md) y [docs/api/dtos-sprint-1.md](api/dtos-sprint-1.md)
- Arquitectura: [docs/arquitectura/arquitectura-sprint-1.md](arquitectura/arquitectura-sprint-1.md) y ADRs en `docs/arquitectura/adr/`
- Historias de usuario: `docs/HU-01-Registrar-cliente.txt`, `docs/HU 02 - Inicio de sesión.txt`, `docs/HU - 03 Registro de proveedor de se.txt`, `docs/HU 04 - Cerrar sesión.txt`, `docs/HU 05 - Gestionar roles y permisos.txt`, `docs/HU 06 - Acceso segun rol.txt`
- Modelo de BD formal (Andraus): `docs/bd/modelo/01_modelo_conceptual.md`, `02_modelo_logico.md`, `03_modelo_fisico.md`
