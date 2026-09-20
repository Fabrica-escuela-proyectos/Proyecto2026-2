# Consultas clave — Sprint 1

Este documento identifica las consultas y operaciones de base de datos más relevantes para las Historias de Usuario del Sprint 1. Estas consultas sirven como referencia para el diseño de índices, restricciones, análisis de planes de ejecución y validación de las operaciones principales del sistema.

## 1. Buscar usuario por email

**HU:** HU02 — Inicio de sesión

Consulta utilizada para localizar al usuario durante el proceso de autenticación.

```sql
SELECT
    u.id,
    u.full_name,
    u.email,
    u.phone,
    u.password_hash,
    u.is_active
FROM users u
WHERE u.email = :email;
```

**Índice relacionado:** `users.email` mediante restricción `UNIQUE`.

---

## 2. Verificar existencia de email

**HU:** HU01 / HU03

Permite verificar que no exista otro usuario con el mismo correo antes de realizar un registro.

```sql
SELECT EXISTS (
    SELECT 1
    FROM users
    WHERE email = :email
);
```

**Índice relacionado:** `users.email`.

---

## 3. Verificar existencia de celular

**HU:** HU01

Permite verificar que el número de celular no esté registrado previamente.

```sql
SELECT EXISTS (
    SELECT 1
    FROM users
    WHERE phone = :phone
);
```

**Índice relacionado:** `users.phone`.

---

## 4. Obtener rol activo de un usuario

**HU:** HU02 / HU06

Permite obtener el rol vigente del usuario. Aunque `user_roles` conserva el historial de roles, solamente debe existir un rol activo por usuario.

```sql
SELECT
    r.id,
    r.name
FROM user_roles ur
JOIN roles r
    ON r.id = ur.role_id
WHERE ur.user_id = :userId
  AND ur.revoked_at IS NULL;
```

**Índice relacionado:**

`idx_user_roles_active`

Índice único parcial sobre:

```sql
(user_id)
WHERE revoked_at IS NULL
```

---

## 5. Obtener permisos del usuario

**HU:** HU06 — Acceso según rol

Permite obtener los permisos correspondientes al rol activo del usuario.

```sql
SELECT DISTINCT
    p.id,
    p.name
FROM user_roles ur
JOIN role_permissions rp
    ON rp.role_id = ur.role_id
JOIN permissions p
    ON p.id = rp.permission_id
WHERE ur.user_id = :userId
  AND ur.revoked_at IS NULL;
```

**Índices relacionados:**

* `user_roles.user_id`
* `role_permissions.role_id`
* `role_permissions.permission_id`

---

## 6. Crear sesión

**HU:** HU02 — Inicio de sesión

Después de autenticar correctamente al usuario se crea una sesión controlada por el backend.

```sql
INSERT INTO sessions (
    user_id,
    token_hash,
    created_at,
    expires_at,
    ip_address,
    user_agent
)
VALUES (
    :userId,
    :tokenHash,
    :createdAt,
    :expiresAt,
    :ipAddress,
    :userAgent
);
```

La fecha de expiración no debe superar una hora desde la creación de la sesión.

---

## 7. Consultar sesión activa

**HU:** HU02 / HU04 / HU06

Permite determinar si una sesión sigue activa, no ha sido revocada y no ha expirado.

```sql
SELECT
    s.id,
    s.user_id,
    s.created_at,
    s.expires_at,
    s.revoked_at
FROM sessions s
WHERE s.token_hash = :tokenHash
  AND s.revoked_at IS NULL
  AND s.expires_at > CURRENT_TIMESTAMP;
```

**Índice relacionado:** `sessions.token_hash` mediante restricción `UNIQUE`.

También debe evaluarse el uso de índices sobre `user_id` y `expires_at` para las consultas de sesiones activas.

---

## 8. Revocar sesiones de un usuario

**HU:** HU04 — Cerrar sesión

Permite invalidar las sesiones activas asociadas al usuario.

```sql
UPDATE sessions
SET revoked_at = CURRENT_TIMESTAMP
WHERE user_id = :userId
  AND revoked_at IS NULL
  AND expires_at > CURRENT_TIMESTAMP;
```

**Índice relacionado:** `sessions.user_id`.

La operación debe ejecutarse de acuerdo con la política de invalidación definida para el cierre de sesión.

---

## 9. Obtener proveedor asociado a un usuario

**HU:** HU03 / HU06

Permite identificar la información de proveedor asociada a un usuario.

```sql
SELECT
    p.id,
    p.user_id,
    p.tax_id
FROM providers p
WHERE p.user_id = :userId;
```

**Índice relacionado:** `providers.user_id`.

La columna `user_id` debe mantenerse como `UNIQUE`, debido a la relación 1:1 entre usuario y proveedor.

---

## 10. Obtener negocios de un proveedor

**HU:** HU06

Permite consultar los negocios administrados por un proveedor.

```sql
SELECT
    b.id,
    b.provider_id,
    b.name,
    b.description
FROM businesses b
WHERE b.provider_id = :providerId;
```

**Índice relacionado:** `businesses.provider_id`.

---

## 11. Validar pertenencia de un negocio a un proveedor

**HU:** HU06

Permite comprobar que un proveedor únicamente opere sobre negocios que le pertenecen.

```sql
SELECT EXISTS (
    SELECT 1
    FROM businesses b
    JOIN providers p
        ON p.id = b.provider_id
    WHERE b.id = :businessId
      AND p.user_id = :userId
);
```

Esta consulta es importante porque la autorización del proveedor no depende únicamente de su rol. También debe verificarse la relación entre el usuario, el proveedor y el negocio.

**Índices relacionados:**

* `businesses.id`
* `businesses.provider_id`
* `providers.user_id`

---

## 12. Revocar rol activo

**HU:** HU05 — Gestionar roles y permisos

El cambio de rol conserva el historial. Primero se revoca el rol activo:

```sql
UPDATE user_roles
SET revoked_at = CURRENT_TIMESTAMP
WHERE user_id = :userId
  AND revoked_at IS NULL;
```

Posteriormente se inserta el nuevo rol:

```sql
INSERT INTO user_roles (
    user_id,
    role_id,
    assigned_at
)
VALUES (
    :userId,
    :newRoleId,
    CURRENT_TIMESTAMP
);
```

Ambas operaciones deben ejecutarse dentro de una misma transacción.

El cambio debe quedar registrado mediante auditoría.

---

## 13. Verificar si un usuario es proveedor

**HU:** HU05

Antes de realizar un cambio de rol debe verificarse que el usuario objetivo no corresponda a un proveedor.

```sql
SELECT EXISTS (
    SELECT 1
    FROM providers
    WHERE user_id = :userId
);
```

Si el usuario corresponde a un proveedor, la operación de cambio de rol debe ser rechazada.

Esta regla también se encuentra respaldada a nivel de base de datos mediante el trigger definido para impedir modificaciones de rol sobre usuarios registrados como proveedores.

---

## 14. Registrar evento de auditoría

**HU:** HU01 / HU02 / HU03 / HU04 / HU05 / HU06

Permite registrar acciones relevantes del sistema.

```sql
INSERT INTO audit_logs (
    user_id,
    action,
    entity,
    entity_id,
    metadata,
    ip_address,
    created_at
)
VALUES (
    :userId,
    :action,
    :entity,
    :entityId,
    :metadata,
    :ipAddress,
    CURRENT_TIMESTAMP
);
```

Entre las acciones consideradas se encuentran:

* `LOGIN`
* `LOGIN_FALLIDO`
* `LOGOUT`
* `REGISTRO_USUARIO`
* `REGISTRO_RECHAZADO`
* `REGISTRO_PROVEEDOR`
* `CAMBIO_ROL`
* `CAMBIO_ROL_RECHAZADO`
* `ACTIVACION_MFA`
* `OPERACION_SENSIBLE`

El campo `metadata` no debe contener contraseñas, tokens completos, secretos ni información sensible innecesaria.

---

## 15. Contar intentos de registro por IP

**HU:** HU01 / HU03

Permite controlar temporalmente los intentos de registro provenientes de una misma dirección IP.

```sql
SELECT COUNT(*)
FROM audit_logs
WHERE action IN (
    'REGISTRO_USUARIO',
    'REGISTRO_PROVEEDOR'
)
AND ip_address = :ipAddress
AND created_at >= CURRENT_TIMESTAMP - INTERVAL '15 minutes';
```

El número máximo de intentos y la ventana temporal definitiva deberán ser establecidos por la lógica de la aplicación.

**Índice relacionado:**

`idx_audit_logs_action_ip_created`

sobre:

```text
(action, ip_address, created_at)
```

La tabla de auditoría se utiliza en el Sprint 1 como fuente de información para este control, evitando crear una tabla independiente de rate limiting.

---

## 16. Consultar auditoría de un usuario

**HU:** Todas las HUs que requieran trazabilidad

Permite consultar los eventos asociados a un usuario.

```sql
SELECT
    id,
    action,
    entity,
    entity_id,
    metadata,
    ip_address,
    created_at
FROM audit_logs
WHERE user_id = :userId
ORDER BY created_at DESC;
```

**Índices relacionados:**

* `audit_logs.user_id`
* `audit_logs.created_at`

---

# 17. Consultas críticas priorizadas

Las consultas anteriores representan las operaciones principales identificadas para Sprint 1. Para el análisis de rendimiento e índices se priorizan las siguientes:

| Prioridad | Consulta                              | HU                 | Motivo                                |
| --------- | ------------------------------------- | ------------------ | ------------------------------------- |
| Alta      | Buscar usuario por email              | HU02               | Operación principal de autenticación  |
| Alta      | Obtener rol activo                    | HU02 / HU06        | Control de acceso                     |
| Alta      | Obtener permisos                      | HU06               | Autorización de operaciones           |
| Alta      | Validar sesión activa                 | HU02 / HU04 / HU06 | Seguridad y acceso                    |
| Alta      | Revocar sesiones                      | HU04               | Invalidación de autenticación         |
| Alta      | Validar pertenencia negocio-proveedor | HU06               | Control de acceso por propiedad       |
| Alta      | Cambio de rol con historial           | HU05               | Regla de negocio                      |
| Alta      | Registrar auditoría                   | HU01–HU06          | Trazabilidad y seguridad              |
| Media     | Contar intentos por IP                | HU01 / HU03        | Control de solicitudes                |
| Media     | Verificar email/celular               | HU01 / HU03        | Integridad y prevención de duplicados |

# 18. Índices relacionados

Los índices principales que deben evaluarse a partir de estas consultas son:

| Índice                             | Tabla              | Justificación                              |
| ---------------------------------- | ------------------ | ------------------------------------------ |
| `idx_users_email`                  | `users`            | Búsqueda de usuario y validación de email  |
| `idx_users_phone`                  | `users`            | Validación de celular                      |
| `idx_user_roles_user_id`           | `user_roles`       | Obtener rol de un usuario                  |
| `idx_user_roles_active`            | `user_roles`       | Garantizar un único rol activo             |
| `idx_role_permissions_role_id`     | `role_permissions` | Obtener permisos por rol                   |
| `idx_sessions_user_id`             | `sessions`         | Consultar y revocar sesiones de un usuario |
| `idx_sessions_expires_at`          | `sessions`         | Consultar y gestionar expiración           |
| `idx_audit_logs_user_id`           | `audit_logs`       | Consultar auditoría por usuario            |
| `idx_audit_logs_created_at`        | `audit_logs`       | Consultar auditoría por rango temporal     |
| `idx_businesses_provider_id`       | `businesses`       | Consultar negocios de un proveedor         |
| `idx_audit_logs_action_ip_created` | `audit_logs`       | Control de intentos de registro por IP     |

La existencia definitiva de cada índice deberá validarse mediante los patrones de consulta, volumen estimado y análisis de planes de ejecución. No se deben crear índices únicamente por aparecer en esta lista.
