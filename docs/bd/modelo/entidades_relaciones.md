# Entidades y Relaciones — Base del Modelo Lógico y Físico

## Entidades

| Entidad | Tipo |
|---|---|
| USUARIO | Fuerte |
| ROL | Fuerte |
| PERMISO | Fuerte |
| USUARIO_ROL | Asociativa (N:M) |
| ROL_PERMISO | Asociativa (N:M) |
| PROVEEDOR | Fuerte (extiende USUARIO) |
| NEGOCIO | Fuerte |
| SESION | Fuerte |
| MFA | Fuerte |
| AUDITORIA | Fuerte |

## Relaciones y cardinalidades

| Relación | Cardinalidad |
|---|---|
| USUARIO — USUARIO_ROL — ROL | 1:N:1 (N:M resuelta); 1 rol activo por usuario en un momento dado (histórico permite más) |
| ROL — ROL_PERMISO — PERMISO | 1:N:1 (N:M resuelta) |
| USUARIO — PROVEEDOR | 1 a 0..1 |
| PROVEEDOR — NEGOCIO | 1 a 0..N |
| USUARIO — SESION | 1 a 0..N |
| USUARIO — MFA | 1 a 0..1 |
| USUARIO — AUDITORIA | 0..1 a 0..N |

## Diagrama

```
USUARIO (1) ──< USUARIO_ROL >── (1) ROL
ROL     (1) ──< ROL_PERMISO >── (1) PERMISO
USUARIO (1) ──  (0..1) PROVEEDOR
PROVEEDOR (1) ──< (0..N) NEGOCIO
USUARIO (1) ──< (0..N) SESION
USUARIO (1) ──  (0..1) MFA
USUARIO (0..1) ──< (0..N) AUDITORIA
```

## Reglas consideradas en el modelo lógico/físico
- Un usuario tiene exactamente 1 rol activo (índice único parcial en `user_roles`); cambios de rol se registran, no se sobrescriben.
- Un proveedor no puede cambiar de rol (regla aplicada con trigger).
- Toda sesión expira máximo en 1 hora desde su creación.
- Toda auditoría de registro/login guarda `ip_address` (soporte de rate limiting).
- Email y celular son únicos por usuario.
