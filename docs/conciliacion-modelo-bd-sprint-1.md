# Conciliación del modelo de base de datos — Sprint 1

Comparación entre el modelo formal de BD (`docs/bd/modelo/`, trabajo de Andraus: conceptual, lógico, físico, `script_inicial.sql`, consultas clave) y el esquema que la aplicación **realmente** usa y tiene desplegado (`reservas-backend/src/main/resources/db/migration/V1`–`V4`, verificado corriendo en Render). Fecha: 2026-09-22.

## Resumen ejecutivo

El modelo formal es un trabajo sólido y bien pensado — normalizado, con justificación por tabla, índices razonados desde las consultas reales, y una relectura cuidadosa de los documentos de HU y errores-API. **No es un modelo "equivocado"**; es un diseño paralelo, hecho sin ver el código, que en varios puntos incluso mejora ideas que el código no tiene (ver sección "Dónde el modelo formal es mejor").

Dicho eso, hoy son **dos esquemas distintos que no se pueden fusionar con un simple `ALTER TABLE`** — difieren en el tipo de clave primaria de punta a punta (UUID vs BIGSERIAL), y hay **un conflicto real, no solo cosmético**: la política `ON DELETE RESTRICT` del modelo formal, aplicada literalmente, haría imposible cumplir el escenario de HU-05 "administrador elimina usuario", que ya verificamos funcionando en la app real.

**Recomendación:** no migrar la app (ya probada y desplegada) a BIGSERIAL. Actualizar los documentos del modelo formal para que describan el esquema que la app realmente aplica, corrigiendo en el camino el problema del `ON DELETE RESTRICT`. Ver sección final para el detalle de qué cambiaría en cada documento.

## Comparación tabla por tabla

### `users`

| | Modelo formal | Esquema real (V1) |
|---|---|---|
| PK | `id BIGSERIAL` | `id UUID DEFAULT gen_random_uuid()` |
| Celular | `phone VARCHAR(30)` | `phone_number VARCHAR(20)` |
| Estado | `is_active BOOLEAN` | `enabled BOOLEAN` |
| Timestamps | `created_at` + `updated_at` | solo `created_at` |

Diferencias solo de nombre (`phone`/`is_active`), sin impacto funcional. `updated_at` no tiene caso de uso real todavía: ninguna operación de Sprint 1 modifica las columnas propias de `users` después de creado (el cambio de rol solo toca `user_roles`).

### `roles`

| | Modelo formal | Esquema real (V1) |
|---|---|---|
| PK | `BIGSERIAL` | `UUID` |
| Columnas | `name`, `description` | solo `name` |
| Seed | `CLIENTE`, `PROVEEDOR`, **`ADMIN`** | `CLIENTE`, `PROVEEDOR`, **`ADMINISTRADOR`** |

**Esto sí es un problema real, no cosmético.** El código entero (`RoleName.java`, `@PreAuthorize("hasRole('ADMINISTRADOR')")` en `UserController`, `AdminBootstrapRunner`) usa el literal `ADMINISTRADOR`. Si `script_inicial.sql` se ejecutara tal cual contra la app, el seed insertaría `'ADMIN'` y **ninguna verificación de rol de administrador volvería a funcionar** — Spring Security busca la autoridad `ROLE_ADMINISTRADOR`, que nunca existiría. `description` no se usa en ningún lado del código; es una columna informativa de más, sin conflicto.

### `user_roles`

| | Modelo formal | Esquema real (V1) |
|---|---|---|
| PK | `(user_id, role_id)` + `assigned_at`, `revoked_at` | `(user_id, role_id)`, sin historial |
| Regla "un solo rol activo" | índice único parcial en BD (`WHERE revoked_at IS NULL`) | se aplica en código: `changeRole` reemplaza el `Set<Role>` completo |

Este es el ejemplo más claro de una **idea genuinamente mejor en el modelo formal**: guardar el historial completo de cambios de rol directamente en la tabla relacional, en vez de solo inferirlo de `audit_logs` (que sí registra cada `CAMBIO_ROL`, pero como texto libre en `detail`, no como filas consultables). Adoptarlo hoy implicaría cambiar `UserManagementServiceImpl.changeRole()` de "reemplazar el set" a "revocar fila activa + insertar nueva", y probablemente pasar de `@ManyToMany` simple a una entidad de asociación explícita (`UserRole`) para poder mapear `assigned_at`/`revoked_at`. Es una mejora real, pero no gratis — vale la pena para un sprint futuro, no ahora.

### `permissions` / `role_permissions`

**No existen en el esquema real**, y es intencional: `AuthorizationServiceImpl.hasPermission()` lanza `UnsupportedOperationException` a propósito porque ninguna de las 6 HU de Sprint 1 necesita permisos granulares — todas se resuelven con rol + pertenencia del recurso (ver `docs/verificacion-criterios-aceptacion-sprint-1.md`). El modelo formal las incluye siguiendo el modelo conceptual original, más orientado a sprints futuros. No es una omisión del código, es una diferencia de alcance ya documentada.

### `providers`

| | Modelo formal | Esquema real (V3) |
|---|---|---|
| PK | `BIGSERIAL` | `UUID` |
| Columnas extra | `tax_id VARCHAR(50) UNIQUE` | — |
| FK a `users` | `ON DELETE RESTRICT` | `ON DELETE CASCADE` |

`tax_id` no lo pide ningún Gherkin de HU-03 (solo nombre, correo, celular, contraseña, nombre del negocio); es alcance adicional del modelo formal, no usado. La diferencia de `ON DELETE` es la misma discusión de fondo que en `audit_logs` — ver más abajo.

### `businesses`

Igual patrón: modelo formal agrega `description VARCHAR(500)`, no usado en ningún DTO ni Gherkin de HU-03. Sin conflicto, solo alcance extra.

### `sessions`

| | Modelo formal | Esquema real (V2) |
|---|---|---|
| PK | `BIGSERIAL` | `UUID` |
| Identificador de token | `token_hash` | `token_id` (el `jti` del JWT, en texto) |
| Columnas extra | `ip_address`, `user_agent` | — |
| Regla de 1 hora | `CHECK (expires_at <= created_at + interval '1 hour')` a nivel de BD | solo aplicada en `JwtTokenProvider` (código) |
| FK a `users` | `ON DELETE RESTRICT` | `ON DELETE CASCADE` |

`token_hash` sugiere hashear el token antes de guardarlo; el código guarda el `jti` (un UUID aleatorio de 128 bits generado por el servidor) en texto plano, sin necesidad de hashearlo — no hay ningún secreto que proteger ahí (el `jti` solo": sirve para revocar, no para autenticar por sí mismo). Es una diferencia de nombre/intención, no una brecha de seguridad real. `ip_address`/`user_agent` no se capturan en ningún lado del código hoy (aunque `originIp` sí se usa para auditoría, no se persiste en `sessions`).

El `CHECK` de 1 hora **sí vale la pena adoptarlo**: es una mejora de defensa en profundidad de bajo riesgo — se puede agregar con una migración nueva (`V5__add_session_max_duration_check.sql`) sin tocar ningún código Java, porque el código ya siempre calcula `expires_at = created_at + 1h`.

### `mfa`

La tabla mejor alineada de las dos: mismas columnas (`user_id`, `secret`, `enabled`, `created_at`), mismo `UNIQUE` en `user_id`. Solo difiere el tamaño de `secret` (255 vs 64 — 64 es más que suficiente para un secreto TOTP en Base32) y el tipo de PK.

### `audit_logs` — la diferencia más grande

| | Modelo formal | Esquema real (V1) |
|---|---|---|
| Columnas | `user_id` (FK), `action`, `entity`, `entity_id`, `metadata JSONB`, `ip_address` | `event_type`, `subject_email`, `outcome`, `detail`, `origin_ip` |
| Éxito/fallo | acciones separadas (`LOGIN` vs `LOGIN_FALLIDO`, `CAMBIO_ROL` vs `CAMBIO_ROL_RECHAZADO`) | un solo `event_type` + columna `outcome` (`SUCCESS`/`REJECTED`) |
| FK a `users` | `ON DELETE RESTRICT` | **sin FK** (guarda `subject_email` como texto) |

El diseño real de `outcome` como columna separada es, en mi opinión, más limpio que duplicar cada acción en una variante de éxito y otra de fallo — evita que el catálogo de acciones crezca al doble cada vez que se agrega un evento nuevo.

**El punto más importante de todo este documento** está en la última fila: el modelo formal referencia `audit_logs.user_id` hacia `users(id)` con `ON DELETE RESTRICT`. Combinado con la regla general del físico ("`ON DELETE RESTRICT` en FKs hacia `users`: no se elimina un usuario con historial"), esto significa que **ningún usuario que alguna vez haya iniciado sesión, sido auditado, o tenga MFA/proveedor asociado podría eliminarse nunca** — la base de datos rechazaría el `DELETE` con un error de FK. Eso es prácticamente todo usuario real, porque el registro mismo ya genera un evento de auditoría.

Esto **contradice directamente** el escenario de HU-05 ya implementado y verificado ("administrador elimina usuario... se elimina el usuario en su totalidad, guardando historial") — `AuthAndAccessControlIntegrationTest` confirma que el `DELETE` sí funciona hoy, precisamente porque el código real evita el problema de dos maneras: `audit_logs` no tiene FK (guarda un snapshot de texto, `subject_email`), y `sessions`/`providers` usan `ON DELETE CASCADE` en vez de `RESTRICT`.

No es un error de Andraus — es una regla razonable en abstracto ("no perder historial") que, aplicada literalmente con `RESTRICT` en vez de `SET NULL` o `CASCADE` según el caso, entra en conflicto con un requisito ya validado. Vale la pena que el equipo lo discuta explícitamente, no que quede en el documento formal sin corregir.

## El rate limiter: una diferencia de arquitectura, no solo de esquema

`docs/bd/modelo/02_modelo_logico.md` y `consultas-clave-sprint-1.md` diseñan el control de intentos de registro (HU-01/HU-03) como una consulta a `audit_logs` (`idx_audit_logs_action_ip_created`), explícitamente "para evitar crear una tabla aparte de rate limiting". El código real (`RegistrationRateLimiter`) hace algo completamente distinto: un contador **en memoria** (`ConcurrentHashMap`), sin tocar la base de datos en absoluto.

Para una sola instancia (como el despliegue actual en Render) ambos enfoques dan el mismo resultado observable. La diferencia importa solo si el proyecto llegara a correr en más de una instancia a la vez: el contador en memoria no se comparte entre instancias, así que el límite de "5 intentos/10 min" se aplicaría por instancia, no de forma global — el enfoque de Andraus, respaldado por la base de datos compartida, sí sería correcto en ese escenario. Vale la pena dejarlo anotado como limitación conocida, no como algo a resolver ahora.

## Dónde el modelo formal es mejor (vale la pena adoptar, sin urgencia)

1. **Historial de cambios de rol** en `user_roles` (`assigned_at`/`revoked_at` + índice único parcial) — más trazable que depender solo de `audit_logs`.
2. **`CHECK` de duración máxima de sesión** en la propia tabla `sessions` — defensa en profundidad, cero riesgo de agregar ahora mismo con una migración nueva.
3. **Diseño explícito de `permissions`/`role_permissions`** para cuando el proyecto sí necesite autorización granular (HU05/HU06 de sprints futuros) — el código ya deja el gancho (`hasPermission()`), la tabla ya está diseñada para cuando se implemente.

## Dónde el esquema real es mejor (el modelo formal debería alinearse)

1. **UUID como PK**: seguro para exponer en URLs/JWT sin permitir enumerar usuarios por id secuencial; ya es el tipo de todos los DTOs, del `sub` del JWT, y de las 4 migraciones reales. Cambiar a BIGSERIAL ahora significaría reescribir cada entidad, cada DTO, `JwtTokenProvider`, cada repositorio y cada test — un costo alto sin beneficio funcional claro para un proyecto de este tamaño.
2. **`outcome` como columna separada** en vez de duplicar cada acción en variante éxito/fallo.
3. **`audit_logs` sin FK hacia `users`** (o, si se quiere mantener la FK, con `ON DELETE SET NULL`, no `RESTRICT`) — necesario para que HU-05 funcione tal como está especificado.
4. **`ON DELETE CASCADE`** en `providers`/`businesses`/`sessions` hacia `users` — mismo motivo.

## Recomendación de conciliación

**No migrar la app real a BIGSERIAL.** Ya está escrita, probada (85+ tests, verificación manual completa) y desplegada en Render contra UUID — reescribirla ahora es mucho riesgo por ninguna ganancia funcional a este tamaño de proyecto.

**Sí actualizar los documentos del modelo formal** para que describan fielmente lo que la app aplica, y de paso corregir el problema de `RESTRICT`. Concretamente, en `01_modelo_conceptual.md`/`02_modelo_logico.md`/`03_modelo_fisico.md`/`script_inicial.sql`:

1. Cambiar `BIGSERIAL`/`BIGINT` → `UUID` en todas las tablas.
2. Renombrar `phone`→`phone_number`, `is_active`→`enabled` (o documentar el mapeo, como ya hace `02_modelo_logico.md` en su sección "Mapeo DTO ↔ columnas BD" — extenderla).
3. Corregir el seed de rol `ADMIN` → `ADMINISTRADOR`.
4. Cambiar `ON DELETE RESTRICT` → `CASCADE` (providers/businesses/sessions/mfa hacia users) o `SET NULL` (audit_logs, si se decide mantenerle la FK).
5. Marcar `permissions`/`role_permissions` explícitamente como "diseño para sprint futuro, no implementado en Sprint 1" en vez de presentarlas como parte del esquema actual.
6. Anotar la diferencia del rate limiter (BD vs en memoria) como limitación conocida del Sprint 1.

Esto es trabajo de documentación, no de código — se lo puedo dejar armado a Andraus como propuesta de edición, o directamente aplicar los cambios en los `.md` si me confirmas que quieres que lo haga yo. No toqué ninguno de sus archivos todavía porque son su entregable de autoría.

## Referencias

- Modelo formal: `docs/bd/modelo/01_modelo_conceptual.md`, `02_modelo_logico.md`, `03_modelo_fisico.md`, `entidades_relaciones.md`, `script_inicial.sql`, `consultas-clave-sprint-1.md`
- Esquema real: `reservas-backend/src/main/resources/db/migration/V1__create_identity_schema.sql` a `V4__create_mfa_table.sql`
- Verificación de que el esquema real cumple los criterios de HU: [docs/verificacion-criterios-aceptacion-sprint-1.md](verificacion-criterios-aceptacion-sprint-1.md)
- Riesgo ya anotado previamente en [docs/estado-proyecto-sprint-1.md](estado-proyecto-sprint-1.md), sección "Dos modelos de base de datos sin conciliar"
