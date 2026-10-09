# Endpoints del Sprint 2

Contratos de los endpoints nuevos del Sprint 2. Errores comunes (formato, 401/403/404/409/429, MFA): ver [errores-api-sprint-2.md](errores-api-sprint-2.md). Los campos y topes marcados como *supuesto* se confirman con QA/PO.

## HU-09 — Crear servicio (implementado)

Solo el **PROVEEDOR dueño del negocio**. El negocio sale de la ruta, nunca del cuerpo. Un ADMINISTRADOR o un proveedor ajeno reciben 403.

### `POST /api/v1/businesses/{businessId}/services`

```json
{ "name": "Corte de cabello", "description": "Incluye lavado", "durationMinutes": 45, "priceCop": 30000 }
```

| Campo | Regla |
|---|---|
| `name` | Obligatorio, máx. 150; se recorta; único por negocio **sin distinguir mayúsculas** |
| `description` | Opcional, máx. 500; en blanco se guarda como ausente |
| `durationMinutes` | Obligatorio, entero 1..1440 (*supuesto*: tope de un día) |
| `priceCop` | Obligatorio, entero 0..1.000.000.000 (*supuesto*); `0` = gratuito |

Respuesta `201`:

```json
{ "id": "…", "businessId": "…", "name": "Corte de cabello", "description": "Incluye lavado",
  "durationMinutes": 45, "priceCop": 30000, "active": true, "createdAt": "2026-10-08T…Z" }
```

El servicio nace **activo**. Se audita (`CREACION_SERVICIO`, quién y cuándo).

| Código | Cuándo |
|---|---|
| 400 | Campo faltante o inválido (`fields` por campo), tipo incorrecto (`"abc"`), `businessId` que no es UUID |
| 401 | Sin sesión |
| 403 | Rol CLIENTE o ADMINISTRADOR, o negocio de otro proveedor |
| 404 | El negocio no existe |
| 409 | Ya existe un servicio con ese nombre en el negocio (también si dos peticiones llegan a la vez) |

### `GET /api/v1/businesses/{businessId}/services`

Lista los servicios del negocio (activos e inactivos) en orden de creación; lista vacía si no hay. Mismas reglas de acceso (401/403/404).

> **Pendiente de HU-09:** "sin recursos asignados no se puede reservar" se aplica en HU-18/HU-20/HU-22, no aquí.

## HU-08 — Definir antelación mínima de reserva (implementado)

Solo el **PROVEEDOR dueño del negocio** (otro proveedor, cliente o administrador: 403; sin sesión: 401; negocio inexistente: 404). Cada negocio tiene la suya; por defecto **1 hora**.

> **Desviación del diseño inicial:** el `GET` de la antelación vive en `/booking-lead-time` (no en `GET /businesses/{businessId}`) para dejar libre esa ruta al detalle del catálogo de HU-13.

### `GET /api/v1/businesses/{businessId}/booking-lead-time`
Respuesta `200`: `{ "businessId": "…", "hours": 1 }`

### `PUT /api/v1/businesses/{businessId}/booking-lead-time`
```json
{ "hours": 2 }
```
`hours`: entero 1..720 (*supuesto*: tope de 30 días). Respuesta `200` con el mismo formato del `GET`. Valores inválidos (`0`, `-2`, `721`, `"abc"`, vacío, nulo) → `400` y **se conserva el valor anterior**. El cambio se audita (`CONFIGURACION_NEGOCIO`).

**Para HU-22:** `BusinessSettingsService.minAdvanceHoursOf(businessId)` devuelve la antelación para exigir "inicio ≥ ahora + antelación" al crear una reserva. Los casos CP-HU08-07 (no afecta reservas ya confirmadas) y CP-HU08-08 (se aplica en HU-22) se prueban cuando exista el módulo de reservas.
