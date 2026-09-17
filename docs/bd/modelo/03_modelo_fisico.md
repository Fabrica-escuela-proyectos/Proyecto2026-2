# Modelo Físico (PostgreSQL) — Sprint 1

## Convenciones de nombres
- Tablas: snake_case, plural (`users`, `role_permissions`).
- PK: `id BIGSERIAL`.
- FK: `<entidad_singular>_id`.
- Timestamps: `created_at`, `updated_at`, `revoked_at`, `expires_at` en `TIMESTAMPTZ`.
- Booleanos: prefijo `is_`.

## Tipos de datos concretos
- Identificadores: `BIGSERIAL` / `BIGINT`.
- Texto corto: `VARCHAR(n)`; texto libre: `TEXT`.
- Fechas/horas: `TIMESTAMPTZ`.
- Estructuras flexibles (auditoría): `JSONB`.
- Booleanos: `BOOLEAN`.

## Índices iniciales y justificación
| Índice | Tabla | Motivo |
|---|---|---|
| `idx_users_email` | users | login por email (HU02) |
| `idx_users_phone` | users | validación de unicidad y búsqueda (HU01) |
| `idx_user_roles_user_id` | user_roles | obtener roles de usuario (HU02/HU06) |
| `idx_role_permissions_role_id` | role_permissions | validar permisos (HU06) |
| `idx_sessions_user_id` | sessions | listar sesiones activas de un usuario (HU04) |
| `idx_sessions_expires_at` | sessions | limpieza/validación de expiración (HU02/HU04) |
| `idx_audit_logs_user_id` | audit_logs | trazabilidad por usuario |
| `idx_audit_logs_created_at` | audit_logs | consultas de auditoría por rango de fecha |
| `idx_businesses_provider_id` | businesses | validar pertenencia negocio↔proveedor (HU06) |
| `idx_user_roles_active` (único parcial, `user_id` WHERE `revoked_at IS NULL`) | user_roles | garantizar un solo rol activo por usuario (HU05, `LoginResponse.role`) |
| `idx_audit_logs_action_ip_created` | audit_logs | soportar conteo de intentos por IP para `429` (HU01, HU03 — errores-api §9) |

## Restricciones de integridad
- `ON DELETE RESTRICT` en FKs hacia `users` (no se elimina un usuario con historial).
- `ON DELETE CASCADE` en `user_roles` y `role_permissions` (tablas puramente asociativas).
- `CHECK` en `sessions.expires_at` (máximo 1 hora desde `created_at`).
- `UNIQUE` en `users.email`, `users.phone`, `sessions.token_hash`, `providers.user_id`, `mfa.user_id`.

## Seguridad a nivel de BD
- Usuario de aplicación (`app_user`) con privilegios mínimos: `SELECT, INSERT, UPDATE` sobre tablas de negocio; sin `DROP`/`TRUNCATE`/`ALTER`.
- Sin acceso directo de la aplicación a tablas de auditoría para `DELETE` o `UPDATE` (solo `INSERT`/`SELECT`).
- `password_hash` y `mfa.secret` nunca expuestos en vistas ni replicados a logs.
- Rol de administración de esquema (`schema_admin`) separado del rol de aplicación.

## Regla de negocio a nivel de BD (nueva, según endpoints-sprint-1.md §6)
`PATCH /api/v1/users/{userId}/role` prohíbe modificar el rol de un usuario `PROVEEDOR`. Esta regla cruza `user_roles` y `providers`, por lo que no puede expresarse como `CHECK` simple: se implementa mediante un **trigger** (`trg_block_provider_role_change`) que rechaza cualquier `INSERT`/`UPDATE` en `user_roles` cuando el usuario objetivo ya existe en `providers`. La aplicación conserva la responsabilidad de rechazar también el auto-cambio de rol y de exigir MFA al promover a `ADMIN` (no expresable en BD).

## Migraciones
Numeradas y versionadas en `migrations/` (formato tipo Flyway `V{n}__descripcion.sql`), una por bloque funcional para permitir rollback y trazabilidad de cambios.
