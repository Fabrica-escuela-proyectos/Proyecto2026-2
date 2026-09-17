# Modelo Conceptual — Sprint 1 (Identidad y Acceso)

## Entidades

- USUARIO
- ROL
- PERMISO
- USUARIO_ROL (asociativa)
- ROL_PERMISO (asociativa)
- PROVEEDOR
- NEGOCIO
- SESION
- MFA
- AUDITORIA

## Relaciones

```
USUARIO (1) ──< USUARIO_ROL >── (1) ROL
ROL     (1) ──< ROL_PERMISO >── (1) PERMISO
USUARIO (1) ──  (0..1) PROVEEDOR
PROVEEDOR (1) ──< (0..N) NEGOCIO
USUARIO (1) ──< (0..N) SESION
USUARIO (1) ──  (0..1) MFA
USUARIO (0..1) ──< (0..N) AUDITORIA
```

## Justificación por entidad

| Entidad | Razón de existencia |
|---|---|
| USUARIO | Identidad base de cualquier actor autenticable (cliente, proveedor, admin) |
| ROL | Requerido por HU05/HU06 para RBAC |
| PERMISO | Granularidad de autorización dentro de un rol |
| USUARIO_ROL | Relación N:M usuario↔rol (un usuario podría tener más de un rol) |
| ROL_PERMISO | Relación N:M rol↔permiso |
| PROVEEDOR | Extiende USUARIO con datos propios del rol Proveedor (HU03) |
| NEGOCIO | Recurso que pertenece a un proveedor; base de la restricción de HU06 |
| SESION | Requerida por HU02/HU04 (login, logout, expiración, revocación) |
| MFA | Requerido para cuentas administrativas (HU05) |
| AUDITORIA | Trazabilidad de acciones críticas (todas las HU) |

## Cardinalidades clave
- Un USUARIO puede tener 1 o más ROLES (mínimo 1 tras registro).
- Un ROL puede tener 0 o más PERMISOS.
- Un USUARIO es PROVEEDOR como máximo una vez (0..1).
- Un PROVEEDOR puede tener 0 o más NEGOCIOS (MVP: normalmente 1, se deja abierto a N).
- Un USUARIO puede tener múltiples SESIONES activas o históricas.
- Un USUARIO tiene 0 o 1 configuración de MFA.
- Una AUDITORIA referencia 0 o 1 USUARIO (0 para eventos de sistema).
