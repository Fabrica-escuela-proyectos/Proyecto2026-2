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

## HU-19 — Definir horarios de atención de un recurso (implementado)

Solo el **PROVEEDOR dueño del negocio del recurso** (cliente: 403; recurso de otro proveedor: 403; sin sesión: 401; recurso inexistente: 404; `resourceId` que no es UUID: 400). Días ISO-8601: **1 = lunes … 7 = domingo**. Horas `HH:mm` (00:00–23:59) en hora local del negocio, zona **America/Bogota**. Un día sin rangos es **no disponible**; un recurso sin horario no se puede reservar (lo exigen HU-20/HU-22).

Los rangos son semiabiertos `[inicio, fin)`: `09:00-12:00` y `12:00-14:00` **no** se superponen. *Límite conocido:* el último minuto expresable es `23:59` (no hay `24:00`).

### `GET /api/v1/resources/{resourceId}/availability`
Siempre devuelve los 7 días (los no disponibles con `ranges: []`):
```json
{ "resourceId": "…", "timezone": "America/Bogota",
  "days": [ { "dayOfWeek": 1, "ranges": [ { "start": "09:00", "end": "12:00" }, { "start": "14:00", "end": "18:00" } ] },
            { "dayOfWeek": 2, "ranges": [] }, "…", { "dayOfWeek": 7, "ranges": [] } ] }
```

### `PUT /api/v1/resources/{resourceId}/availability`
Reemplaza **toda la semana**; los días omitidos quedan no disponibles.
```json
{ "days": [ { "dayOfWeek": 1, "ranges": [ { "start": "09:00", "end": "17:00" } ] } ] }
```
### `PUT /api/v1/resources/{resourceId}/availability/{dayOfWeek}`
Reemplaza **solo ese día**; los demás días y los demás recursos no cambian. `{ "ranges": [] }` deja el día no disponible.
```json
{ "ranges": [ { "start": "08:00", "end": "13:00" } ] }
```
Ambos responden `200` con el horario completo vigente (mismo formato del `GET`).

**Errores `400` (el recurso conserva su horario anterior; la operación es atómica):** formato de hora inválido (`ab:cd`, `25:00`, `9:00`, `24:00`, vacío) → *"El rango horario no es válido: use el formato HH:mm…"*; inicio ≥ fin (`14:00-10:00`, `09:00-09:00`) → *"…la hora de inicio debe ser anterior a la de fin"*; rangos del mismo día superpuestos (incluido uno contenido en otro) → *"Los rangos horarios de un mismo día no pueden superponerse"*; día fuera de 1..7 o repetido en la semana; más de 10 rangos en un día (*supuesto*); lista ausente o nula. Dos ediciones simultáneas del mismo recurso se serializan (bloqueo de la fila del recurso). Cada cambio se audita (`DISPONIBILIDAD_RECURSO`).

> **Pendiente de HU-19:** los escenarios "reserva dentro/fuera del horario" (CP del motor de reservas) se prueban cuando exista HU-22; esta HU solo guarda el horario.

## HU-20 — Consultar disponibilidad de un servicio (implementado)

### `GET /api/v1/services/{serviceId}/availability?date=yyyy-MM-dd`

Solo lectura. **Hoy exige sesión iniciada** (cualquier rol; sin sesión: 401), por la decisión del 2026-10-08.

> **⚠ Conflicto abierto con el criterio de aceptación.** La HU incluye el escenario *"Consulta de horarios libres por parte de un usuario sin sesión iniciada → el sistema permite ver los horarios disponibles sin requerir autenticación"*, y el caso de prueba `CP-HU20-09` lo verificará. La decisión tomada (exigir sesión) lo incumple. Cambiarlo es una línea en `SecurityConfig` (`.requestMatchers(HttpMethod.GET, "/api/v1/services/*/availability").permitAll()`) más actualizar la prueba `sinSesionDevuelve401`. **Falta que Simon confirme cuál prevalece.**

`date` es opcional: sin fecha se usa **hoy** (hora de Bogotá). *Ambigüedad:* el escenario dice "a partir de la fecha actual"; se interpretó como los horarios de hoy, no un rango.

**Cómo se calculan los horarios:** para cada **recurso activo** asignado al servicio (HU-18) se toman los rangos de ese día de la semana (HU-19) y se parten en horarios consecutivos de la **duración del servicio** (supuesto: paso = duración; con 45 min y rango 09:00-12:00 → 09:00, 09:45, 10:30, 11:15; los que no caben completos se descartan). Se descuentan los horarios que empiezan antes de **ahora + antelación mínima del negocio** (HU-08) y los que se traslapan con tiempos ocupados (reservas confirmadas; punto de extensión `BusyTimeSource`, que implementará HU-22). Sin caché: un cambio de horario del recurso se ve en la consulta siguiente.

```json
{ "serviceId": "…", "serviceName": "Corte", "date": "2026-10-19", "timezone": "America/Bogota", "durationMinutes": 60,
  "slots": [ { "start": "09:00", "end": "10:00", "resources": [ { "id": "…", "name": "Sala 1" } ] } ],
  "message": null }
```
Un mismo horario atendible por varios recursos sale **una sola vez** con todos sus recursos (HU-22 recibe `startAt` y, opcionalmente, un recurso). `message` solo viene sin horarios: *"No hay horarios disponibles ese día: el servicio no tiene horario definido"* (día sin horario, sin recursos o recursos inactivos) o *"No quedan horarios disponibles ese día"* (todo ocupado o fuera de antelación).

| Código | Cuándo |
|---|---|
| 400 | Fecha con formato inválido (`32/13/2026`, `ab/cd/efgh`, `2026-99-99`, `2026-02-30`, `2026-1-5`), anterior a hoy (*"Debe seleccionar una fecha futura o la fecha actual"*), a más de 365 días (*supuesto*), o `serviceId` que no es UUID |
| 401 | Sin sesión (ver el conflicto de arriba) |
| 404 | *"El servicio no está disponible"*: no existe, está inactivo o la cuenta de su proveedor está inactiva (mismo mensaje en todos los casos) |

Dos consultas simultáneas ven el mismo horario libre (es de solo lectura); en cuanto una reserva exista (HU-22) el horario deja de mostrarse al otro cliente.
