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

## HU-13 — Consultar negocios y servicios (implementado)

Solo lectura, para **cualquier usuario autenticado** (cliente, proveedor o administrador); sin sesión: 401. **Decidido (2026-10-08):** HU-13 y HU-20 exigen un cliente con sesión iniciada; el AC de HU-20 dice "un cliente consulta".

### `GET /api/v1/businesses?page=0&size=20`
Negocios ordenados por nombre (sin distinguir mayúsculas). `page` desde 0 (defecto 0); `size` defecto 20 y **tope 50** (uno mayor se recorta a 50). `page < 0` o `size < 1` → 400; no numérico → 400; una página fuera de rango devuelve lista vacía con 200.

```json
{ "items": [ { "id": "…", "name": "Barbería Central" } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "message": null }
```
`message` solo viene con el catálogo realmente vacío (`"Aún no hay negocios registrados"`).

### `GET /api/v1/businesses/{businessId}`
```json
{ "id": "…", "name": "Barbería Central",
  "services": [ { "id": "…", "name": "Corte", "description": "Con lavado", "durationMinutes": 45, "priceCop": 30000 } ],
  "message": null }
```
Solo servicios **activos**, ordenados por nombre. Sin servicios activos: `services: []` y `message: "Este negocio aún no tiene servicios disponibles"`. Negocio inexistente → 404; `businessId` que no es UUID → 400.

## HU-14 — Registrar recurso (implementado)

Solo el **PROVEEDOR dueño del negocio** (cliente o administrador: 403; sin sesión: 401; negocio ajeno: 403; negocio inexistente: 404). El negocio sale de la **ruta**; si el cuerpo trae un `businessId` (aunque sea de otro negocio) **se ignora**, como pide el escenario "Intentar asociar el recurso a otro negocio".

### `POST /api/v1/businesses/{businessId}/resources`
```json
{ "name": "Sala 1", "type": "SALA" }
```
| Campo | Regla |
|---|---|
| `name` | Obligatorio, máx. 150; se recorta; único por negocio **sin distinguir mayúsculas** (409) |
| `type` | Obligatorio; `SALA`, `EQUIPO` o `PERSONAL` (cualquier combinación de mayúsculas; se guarda en mayúsculas). *Supuesto:* lista fija, a confirmar con QA/PO frente a "consultorio, sala, cancha, puesto" |

Respuesta `201`: `{ "id": "…", "businessId": "…", "name": "Sala 1", "type": "SALA", "active": true, "createdAt": "…" }`. El recurso nace **activo** y se audita (`REGISTRO_RECURSO`). Errores `400` por campo (`fields.name`, `fields.type`; un tipo fuera de la lista dice "El tipo debe ser SALA, EQUIPO o PERSONAL").

### `GET /api/v1/businesses/{businessId}/resources`
Lista los recursos del negocio (activos e inactivos) en orden de creación; vacía si no hay. Mismas reglas de acceso.

## HU-18 — Asignar recursos a un servicio (implementado)

Solo el **PROVEEDOR dueño del negocio del servicio** (cliente o administrador: 403; sin sesión: 401; servicio de otro proveedor: 403; servicio inexistente: 404). Es requisito de HU-20 (disponibilidad) y HU-22 (reserva).

### `PUT /api/v1/services/{serviceId}/resources`
```json
{ "resourceIds": ["<uuid de recurso>", "<uuid de recurso>"] }
```
**Reemplaza** el conjunto de recursos del servicio (idempotente: repetir la petición no cambia nada). Permite varios recursos por servicio; los ids repetidos cuentan una vez; una lista **vacía** deja el servicio sin recursos; máximo 100 ids (*supuesto*).

**Atómico:** si algún recurso no existe o pertenece a otro negocio, responde `400` con *"Uno o más recursos no existen o no pertenecen al negocio del servicio"* (mismo mensaje en ambos casos, para no revelar si un id es de otro negocio) y **no se cambia nada**, ni lo nuevo ni lo que ya estaba asignado. Respuesta `200`:

```json
{ "serviceId": "…", "resources": [ { "id": "…", "name": "Sala 1", "type": "SALA", "active": true } ] }
```
Los recursos vienen ordenados por nombre. Dos `PUT` simultáneos sobre el mismo servicio se serializan (bloqueo de la fila del servicio). Se audita (`ASIGNACION_RECURSOS`) solo cuando el conjunto cambia. Cuerpo ausente, `resourceIds` nulo o con ids que no son UUID → `400`.

### `GET /api/v1/services/{serviceId}/resources`
Mismo formato de respuesta; lista vacía si no tiene recursos. Mismas reglas de acceso.

> Se pueden asignar recursos **inactivos** (podrían reactivarse con HU-17); la disponibilidad (HU-20) solo debe contar los activos.
