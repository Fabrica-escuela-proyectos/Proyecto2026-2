# Modelo Lógico — Sprint 1 (Identidad y Acceso)

## USERS
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT | PK |
| full_name | VARCHAR(150) | NOT NULL |
| email | VARCHAR(150) | UNIQUE, NOT NULL |
| phone | VARCHAR(30) | UNIQUE, NOT NULL |
| password_hash | VARCHAR(255) | NOT NULL |
| is_active | BOOLEAN | NOT NULL, DEFAULT true |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

## ROLES
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT | PK |
| name | VARCHAR(50) | UNIQUE, NOT NULL (CLIENTE, PROVEEDOR, ADMIN) |
| description | VARCHAR(255) | |

## USER_ROLES
| Columna | Tipo | Restricción |
|---|---|---|
| user_id | BIGINT | PK, FK → users(id) |
| role_id | BIGINT | PK, FK → roles(id) |
| assigned_at | TIMESTAMP | NOT NULL |
| revoked_at | TIMESTAMP | NULL |

> Cambio (endpoints/DTOs Sprint 1): `LoginResponse.role` y `PATCH /users/{id}/role` tratan el rol como único y reemplazable, no acumulable. Se conserva la tabla N:M por trazabilidad, pero se agrega `revoked_at` + índice único parcial (`user_id` WHERE `revoked_at IS NULL`) para garantizar **un solo rol activo por usuario**. El cambio de rol = revocar fila activa + insertar nueva fila (auditado como `CAMBIO_ROL`).

## PERMISSIONS
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT | PK |
| name | VARCHAR(80) | UNIQUE, NOT NULL |
| description | VARCHAR(255) | |

## ROLE_PERMISSIONS
| Columna | Tipo | Restricción |
|---|---|---|
| role_id | BIGINT | PK, FK → roles(id) |
| permission_id | BIGINT | PK, FK → permissions(id) |

## PROVIDERS
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | UNIQUE, NOT NULL, FK → users(id) |
| tax_id | VARCHAR(50) | UNIQUE |
| created_at | TIMESTAMP | NOT NULL |

## BUSINESSES
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT | PK |
| provider_id | BIGINT | NOT NULL, FK → providers(id) |
| name | VARCHAR(150) | NOT NULL |
| description | VARCHAR(500) | |
| created_at | TIMESTAMP | NOT NULL |

## SESSIONS
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | NOT NULL, FK → users(id) |
| token_hash | VARCHAR(255) | UNIQUE, NOT NULL |
| created_at | TIMESTAMP | NOT NULL |
| expires_at | TIMESTAMP | NOT NULL, CHECK (expires_at <= created_at + interval '1 hour') |
| revoked_at | TIMESTAMP | NULL |
| ip_address | VARCHAR(45) | |
| user_agent | VARCHAR(255) | |

## MFA
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | UNIQUE, NOT NULL, FK → users(id) |
| secret | VARCHAR(255) | NOT NULL |
| enabled | BOOLEAN | NOT NULL, DEFAULT false |
| created_at | TIMESTAMP | NOT NULL |

## AUDIT_LOGS
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | NULL, FK → users(id) |
| action | VARCHAR(50) | NOT NULL (LOGIN, LOGIN_FALLIDO, LOGOUT, REGISTRO_USUARIO, REGISTRO_RECHAZADO, REGISTRO_PROVEEDOR, CAMBIO_ROL, CAMBIO_ROL_RECHAZADO, CAMBIO_PERMISO, ACTIVACION_MFA, OPERACION_SENSIBLE) |
| entity | VARCHAR(50) | |
| entity_id | BIGINT | |
| metadata | JSONB | |
| ip_address | VARCHAR(45) | |
| created_at | TIMESTAMP | NOT NULL |

> Regla: `metadata` nunca debe contener contraseñas, tokens completos ni secretos.
> Cambio (errores-api Sprint 1, sección 9): `ip_address` debe registrarse en **todo** intento de `REGISTRO_USUARIO`/`REGISTRO_PROVEEDOR`/`LOGIN`, exitoso o no, porque `audit_logs` es la fuente de datos para calcular el límite de `429 Too Many Requests` (no se crea tabla aparte de rate limiting).

## Consultas relevantes para el diseño (normalización/índices)
- Buscar usuario por email / celular → requiere índice único ya cubierto por UNIQUE.
- Obtener roles de un usuario → JOIN users–user_roles–roles, índice en user_roles.user_id, filtrando `revoked_at IS NULL`.
- Validar permisos → JOIN roles–role_permissions–permissions.
- Consultar sesiones activas → índice en sessions.user_id + sessions.expires_at.
- Consultar auditoría → índice en audit_logs.user_id y audit_logs.created_at.
- **Nuevo:** contar intentos de registro por IP en una ventana de tiempo (HU01/HU03, error 429) → índice compuesto en audit_logs(action, ip_address, created_at).

## Mapeo DTO (API) ↔ columnas BD
| Campo DTO | Tabla.columna |
|---|---|
| `fullName` | users.full_name |
| `email` | users.email |
| `cellphone` | users.phone |
| `password` | users.password_hash (tras hash, nunca texto plano) |
| `businessName` | businesses.name |
| `role` (login/response) | roles.name vía user_roles activo |
| `role` (PATCH HU05) | user_roles nueva fila + revocación de la anterior |
| `token` | sessions.token_hash (se persiste el hash, no el JWT) |
| `expiresIn` | sessions.expires_at − sessions.created_at (máx. 3600 s) |

## Normalización
Modelo en 3FN: sin atributos repetidos ni dependencias transitivas; N:M resueltas mediante tablas asociativas (USER_ROLES, ROLE_PERMISSIONS).
