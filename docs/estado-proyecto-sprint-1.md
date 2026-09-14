# Estado del proyecto — Sprint 1

Fecha de este corte: 2026-09-13. Alcance: `reservas-backend/` (único código existente en el repo). Basado en lectura directa del código fuente, los tests y la documentación en `docs/`.

## Resumen ejecutivo

El backend implementa **HU-01 (Registro de cliente) de punta a punta**, con validación, reglas de negocio, seguridad de contraseñas, auditoría, rate limiting, manejo de errores uniforme y pruebas unitarias + de integración. Todo lo demás del proyecto (login, proveedores, servicios, recursos, reservas, reportes, control de acceso por rol/permiso) **existe solo como documento de arquitectura o como "insumo técnico" sin conectar**, tal como el propio código lo declara explícitamente en sus comentarios.

Hay un riesgo concreto no resuelto: **el modelo de base de datos que Andraus entregó formalmente (BD conceptual/lógica/física) y el esquema que Flyway realmente aplica son dos diseños distintos e inconciliados** (UUID vs BIGSERIAL, columnas distintas, tablas que solo existen en uno de los dos). Ver sección "Riesgos".

## 1. Qué está desarrollado

### Módulo `identity` (HU-01 completo)

- **Registro de usuario** (`UserController` → `POST /api/v1/users`, único endpoint del proyecto): valida el payload, rechaza email/celular duplicado (409), aplica rate limiting (429, 5 intentos/10 min con bloqueo de 15 min), asigna el rol `CLIENTE` siempre desde el servidor (nunca desde el body), hashea la contraseña con BCrypt, persiste el usuario y registra el evento en auditoría.
- **Validaciones custom**: `@ValidPassword` (mín. 8 caracteres, mayúscula, minúscula, carácter especial) y `@ValidPhone` (celular colombiano `3XXXXXXXXX`, marcado en el propio código como *supuesto a confirmar*).
- **Entidades**: `User`, `Role` (enum `CLIENTE`/`PROVEEDOR`/`ADMINISTRADOR`), `Session` (sin uso todavía).
- **Autorización básica**: `AuthorizationService.hasRole(...)` funciona; `hasPermission(...)` lanza `UnsupportedOperationException` a propósito porque no existe catálogo de permisos aún.
- **`SecurityConfig`** activo: CSRF off, sesiones stateless, solo `/api/v1/users` y `/actuator/health` son públicos, todo lo demás exige autenticación — pero **no hay ningún filtro que autentique nada todavía**, así que en la práctica cualquier otro endpoint futuro quedaría bloqueado hasta que exista login.
- **`JwtTokenProvider`**: genera/valida JWT (HS256, 1h, con `jti`), pero es una utilidad suelta — "nadie la invoca en este sprint" (comentario del propio archivo). Solo tiene tests unitarios directos.

### Módulo `audit`

- `AuditService.registerEvent(...)` persiste eventos en la tabla `audit_logs`. Conectado y en uso real desde `UserRegistrationService` (evento `REGISTRO_USUARIO`, outcome `SUCCESS`/`REJECTED`).
- El enum `AuditEventType` ya declara `LOGIN, LOGOUT, CAMBIO_ROL, CAMBIO_PERMISO, ACTIVACION_MFA, OPERACION_SENSIBLE` para que HU02–HU06 los reutilicen sin renegociar el catálogo — hoy solo `REGISTRO_USUARIO` se usa.

### Módulo `common`

- Formato de error uniforme (`ApiError` + `GlobalExceptionHandler`) para 400 (validación), 409 (conflicto), 429 (rate limit) y 500 (genérico). Los códigos 401/403/404 (asociados a HU02/HU05/HU06) no tienen manejador porque esas historias no existen todavía.

### Infraestructura y calidad

- **Migraciones Flyway** (`V1__create_identity_schema.sql`, `V2__create_sessions_table.sql`): crean `roles`, `users`, `user_roles`, `audit_logs`, `sessions`, con seed de los 3 roles.
- **29 tests** (unitarios + integración con Testcontainers/PostgreSQL real vía `UserRegistrationIntegrationTest`), **27 pasan limpio**; los 2 restantes fallan solo por infraestructura local, no por lógica (ver [docs/resultados-pruebas-sprint-1.md](resultados-pruebas-sprint-1.md)).
- `docker-compose.yml` para levantar Postgres de desarrollo.
- La app arranca y sirve tráfico real en `localhost:8080` con esquema migrado y verificado.

## 2. Qué falta

### Historias de usuario sin ningún código (HU02–HU06)

Documentadas en `docs/api/endpoints-sprint-1.md` pero sin controlador, DTOs, ni lógica:

| HU | Endpoint propuesto | Estado |
|---|---|---|
| HU02 — Inicio de sesión | `POST /api/v1/auth/login` | No implementado. `JwtTokenProvider` existe pero no está conectado a ningún filtro. |
| HU03 — Registro de proveedor | `POST /api/v1/providers` | No implementado. Paquete `provider/` no existe. |
| HU04 — Cerrar sesión | `POST /api/v1/auth/logout` | No implementado. `Session`/`SessionRepository` existen pero sin consumidores. |
| HU05 — Cambio de rol | `PATCH /api/v1/users/{userId}/role` | No implementado. |
| HU06 — Control de acceso transversal | (sin endpoint propio) | No implementado. No hay filtro que resuelva rol/pertenencia. |

### Módulos completos sin ningún código

`provider`, `service`, `resource`, `reservation`, `report` — mencionados en `arquitectura-sprint-1.md` como "preparación arquitectónica" para sprints siguientes; no tienen ni siquiera el paquete Java creado.

### Piezas de seguridad "a medio conectar"

- No hay `JwtAuthenticationFilter` en `SecurityConfig` → el JWT no protege nada todavía.
- `IdentityService.getCurrentUser()` siempre devuelve `Optional.empty()` (comportamiento esperado y documentado mientras no exista login).
- Sin catálogo de permisos granular → `hasPermission()` no se puede implementar todavía.
- Sin MFA (mencionado en ADR-002 para administradores, cero código).

### Documentación de referencia que no existe en el repo

El código cita repetidamente estos archivos en sus comentarios, pero **no están en el repositorio** (confirmado, cero resultados en toda la búsqueda):
- `docs/HU-01-checklist.md`
- `docs/matriz-actualizaciones.md`
- `docs/api-contract-POST-users.md`, `docs/ADR-002-insumos.md`
- `HU-01-Registrar-cliente.txt`, `MatrizProvisionalHU.docx`, `Sprint_1_ArquisuaveBD.docx`

Esto es relevante porque varias decisiones "pendientes de validar" (ej. formato del celular, mensaje único de conflicto vs. mensaje por campo, modelo físico definitivo) apuntan a esos documentos como la fuente de verdad, y hoy no son recuperables desde el repo.

### `docs/bd/modelo/modelo.md` y `docs/bd/consultas/consultas.md`

Están vacíos. La documentación formal de BD real vive en otros archivos (`01_modelo_conceptual.md`, `02_modelo_logico.md`, `03_modelo_fisico.md`, de Andraus) que **no coinciden con el esquema que el backend usa** — ver Riesgos.

## 3. Riesgos / decisiones abiertas

1. **Dos modelos de base de datos sin conciliar.** El modelo formal de Andraus (BD) usa `BIGSERIAL`, agrega tablas `permissions`, `role_permissions`, `providers`, `businesses`, `mfa`, columnas `phone`/`is_active`/`token_hash`, y un trigger para bloquear cambio de rol de proveedor. El esquema que Flyway realmente aplica (y que usa el backend) usa `UUID`, no tiene esas tablas, y usa `phone_number`/`enabled`/`token_id`. **Ambos scripts incluso usan nombres de rol distintos** (`ADMINISTRADOR` vs `ADMIN`). El propio `V1__create_identity_schema.sql` ya advierte que es "una PROPUESTA desde el backend" pendiente de validar contra el modelo formal. Esto se tiene que resolver antes de construir HU03+ (que dependen de `providers`) o HU05/HU06 (que dependen de `permissions`).
2. **Formato de celular sin confirmar**: el validador exige `3XXXXXXXXX` (10 dígitos, Colombia) pero el propio código lo marca como supuesto, no decisión cerrada.
3. **Mensaje de conflicto por email/celular duplicado**: `errores-api-sprint-1.md` pide un mensaje genérico único; el código da un mensaje distinto por campo. Es una desviación consciente pero no hay evidencia de que se haya validado con el equipo.
4. **`ReservasBackendApplicationTests`** no usa Testcontainers (a diferencia de `UserRegistrationIntegrationTest`) — depende de que haya un Postgres real accesible en `localhost:5432`. Con el `docker-compose.yml` nuevo ya cumple esa condición, pero conviene decidir si se homologa a Testcontainers para que no dependa de infraestructura externa.

## 4. Pasos a seguir sugeridos

**Antes de escribir más código de HU02+:**
1. Conciliar el modelo de BD de Andraus con el esquema Flyway real (una sola fuente de verdad: nombres de columna, tipos de PK, catálogo de roles). Esto bloquea HU03 (`providers`) y HU05/HU06 (`permissions`).
2. Recuperar o recrear los documentos de trazabilidad referenciados que no están en el repo (`HU-01-checklist.md`, `matriz-actualizaciones.md`) o quitar las referencias del código si ya no aplican.
3. Confirmar con el equipo el formato de celular y la política de mensajes de conflicto (genérico vs. por campo).

**Para HU02 (Login) — siguiente pieza natural, ya tiene insumos listos:**
4. Implementar `POST /api/v1/auth/login`: `LoginRequest`/`LoginResponse` (ya especificados en `dtos-sprint-1.md`), validar credenciales contra `UserRepository` + `PasswordEncoder`, emitir JWT con `JwtTokenProvider` (ya existe), persistir la sesión en `SessionRepository` (ya existe).
5. Conectar un `JwtAuthenticationFilter` a `SecurityConfig` para que `IdentityService.getCurrentUser()` empiece a devolver el usuario real.

**Después de login:**
6. HU04 (Logout): revocar sesión vía `SessionRepository` usando el `jti` del token.
7. HU05 (Cambio de rol) y HU06 (control de acceso): requieren primero el catálogo de permisos (punto 1).
8. HU03 (Registro de proveedor): requiere primero conciliar el modelo de BD (tabla `providers`).

**Transversal, en cualquier momento:**
9. Decidir si `ReservasBackendApplicationTests` migra a Testcontainers para no depender de un Postgres externo.
10. A medida que se agreguen HU02–HU06, reutilizar lo ya preparado deliberadamente para ellas: `Session`, `SessionRepository`, `JwtTokenProvider`, los valores de `AuditEventType`, y los roles `PROVEEDOR`/`ADMINISTRADOR` ya sembrados en la tabla `roles`.

## Referencias

- Detalle de la corrida de tests y arranque local: [docs/resultados-pruebas-sprint-1.md](resultados-pruebas-sprint-1.md)
- Especificación de endpoints: [docs/api/endpoints-sprint-1.md](api/endpoints-sprint-1.md) y [docs/api/dtos-sprint-1.md](api/dtos-sprint-1.md)
- Arquitectura: [docs/arquitectura/arquitectura-sprint-1.md](arquitectura/arquitectura-sprint-1.md) y ADRs en `docs/arquitectura/adr/`
- Modelo de BD formal (Andraus): `docs/bd/modelo/01_modelo_conceptual.md`, `02_modelo_logico.md`, `03_modelo_fisico.md`
