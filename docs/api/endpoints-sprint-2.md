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

Solo lectura, para **cualquier usuario autenticado** (cliente, proveedor o administrador); sin sesión: 401. El catálogo exige sesión, como dice su historia; la disponibilidad de HU-20, en cambio, es pública (decisión del 2026-10-08).

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

Solo lectura y **pública: no requiere sesión** (decidido el 2026-10-08, según el escenario *"usuario sin sesión iniciada"* del criterio de aceptación y `CP-HU20-09`). Es la única ruta pública de `/api/v1/services/**`: solo el `GET` de `/availability`; la asignación de recursos (HU-18) y todo lo demás siguen exigiendo sesión y rol. Con un token inválido responde igual (se ignora). El catálogo de HU-13 sí exige sesión, como dice su historia.

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
| 404 | *"El servicio no está disponible"*: no existe, está inactivo o la cuenta de su proveedor está inactiva (mismo mensaje en todos los casos) |

Dos consultas simultáneas ven el mismo horario libre (es de solo lectura); en cuanto una reserva exista (HU-22) el horario deja de mostrarse al otro cliente.

## HU-22 — Crear reserva (implementado)

### `POST /api/v1/bookings`

Solo el rol **CLIENTE** (proveedor o administrador: 403; sin sesión: 401). El cliente sale de la sesión, nunca del cuerpo: la reserva queda asociada únicamente a quien la creó.

```json
{ "serviceId": "<uuid>", "date": "2026-10-19", "startTime": "10:00", "endTime": "11:00", "resourceId": "<uuid, opcional>" }
```
| Campo | Regla |
|---|---|
| `serviceId` | Obligatorio; servicio activo de un proveedor con cuenta activa |
| `date` | Obligatoria, `yyyy-MM-dd`, hoy o futura (hora de Bogotá) |
| `startTime`, `endTime` | Obligatorias, `HH:mm`; **fin posterior al inicio**; la duración debe ser **igual a la del servicio** (*supuesto*: precio fijo por servicio, así que no se admiten duraciones libres) |
| `resourceId` | Opcional. Sin él, el sistema asigna el **primer recurso libre por nombre**; con él, debe estar asignado al servicio (HU-18) y activo |

Además, el inicio debe cumplir la **antelación mínima** del negocio (HU-08) y el rango entero debe caber dentro de un rango de atención del recurso ese día (HU-19). El inicio **no tiene que caer en la cuadrícula** que muestra HU-20: cualquier minuto libre dentro del horario es válido (`10:30-11:30` si está libre).

Respuesta `201` (estado inicial `CONFIRMADA`, devuelve el **id**):
```json
{ "id": "…", "status": "CONFIRMADA", "serviceId": "…", "serviceName": "Corte", "businessId": "…", "businessName": "Barbería",
  "resourceId": "…", "resourceName": "Sala 1", "date": "2026-10-19", "startTime": "10:00", "endTime": "11:00",
  "priceCop": 25000, "createdAt": "…" }
```
La reserva guarda **copias** del nombre del servicio, recurso, negocio y del **precio vigente** al reservar (sobreviven a ediciones o eliminaciones) y se audita (`CREACION_RESERVA`).

| Código | Cuándo |
|---|---|
| 400 | Campo obligatorio faltante (`fields.date`, `fields.startTime`…), formato inválido, **fin ≤ inicio** (*"El rango de horas es inválido…"*), fecha pasada, duración distinta a la del servicio, antelación insuficiente, `resourceId` no asignado o inactivo |
| 401 / 403 | Sin sesión / rol distinto de CLIENTE |
| 404 | *"El servicio no está disponible"* (no existe, inactivo o proveedor inactivo) |
| 409 | *"El horario seleccionado no está disponible"*: ya hay una reserva activa **o** cae fuera del horario de atención (mensajes distintos). También ante dos peticiones simultáneas |

**Anti-overbooking en la base de datos:** la tabla `bookings` tiene `EXCLUDE USING gist (resource_id WITH =, tstzrange(start_at, end_at) WITH &&) WHERE (status = 'CONFIRMADA')`: dos reservas confirmadas de un recurso no pueden traslaparse ni aunque lleguen a la vez (una gana con 201 y la otra recibe 409). *Supuesto:* el límite es **por recurso** (un servicio con varios recursos admite varias reservas simultáneas, una por recurso), no por servicio. Una reserva CANCELADA o COMPLETADA no ocupa el horario. Con asignación automática, si otra petición toma el recurso elegido en el mismo instante, esta recibe 409 y puede reintentar.

**Integración con HU-20:** las reservas CONFIRMADAS se descuentan de los horarios libres (`BookingBusyTimeSource`): en cuanto se reserva, el horario deja de mostrarse. HU-19 (CP "reserva dentro/fuera del horario") y HU-08 (CP-HU08-08, antelación) quedan cubiertas con esta HU.

## HU-23 — Consultar mis reservas (implementado)

Solo el rol **CLIENTE** y solo **sus** reservas (proveedor o administrador: 403; sin sesión: 401).

### `GET /api/v1/bookings/me?status=&page=0&size=20`
- **Orden:** de la más reciente a la más antigua por **fecha de inicio** (desempate por id, paginación estable).
- `status` (opcional): `CONFIRMADA`, `CANCELADA` o `COMPLETADA`, sin distinguir mayúsculas; otro valor → 400.
- `page` desde 0 (defecto 0); `size` defecto 20 y **tope 50** (uno mayor se recorta); `page < 0`, `size < 1` o valores no numéricos → 400; una página fuera de rango devuelve lista vacía con 200.

```json
{ "items": [ { "id": "…", "status": "CANCELADA", "serviceId": "…", "serviceName": "Corte", "businessId": "…",
               "businessName": "Barbería", "resourceId": "…", "resourceName": "Sala 1", "date": "2026-10-19",
               "startTime": "10:00", "endTime": "11:00", "priceCop": 25000,
               "cancelReason": "No puedo asistir", "cancelledAt": "2026-10-15T12:00:00Z", "createdAt": "…" } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "message": null }
```
Fecha y horas en hora de Bogotá; el precio es el vigente cuando se reservó. `cancelReason` y `cancelledAt` solo vienen en las canceladas. Sin reservas: lista vacía y `message` *"No tiene reservas registradas"* (con filtro de estado: *"No tiene reservas con el estado indicado"*); una página fuera de rango no lleva mensaje.

### `GET /api/v1/users/{userId}/bookings`
Mismos parámetros y respuesta. Si `userId` es el del propio cliente devuelve su lista; si es **de otro usuario devuelve 403** (error de autorización, sin revelar nada sobre ese usuario; un id inexistente da el mismo 403; un id que no es UUID, 400). Existe para que el escenario *"un cliente intenta ver las reservas de otro cliente"* tenga una ruta real que probar; no hay forma de listar reservas ajenas.

## HU-24 — Consultar reservas del negocio (implementado)

Solo el **PROVEEDOR dueño del negocio** (otro proveedor, cliente o administrador: 403; sin sesión: 401; negocio inexistente: 404; `businessId` que no es UUID: 400). Muestra **todas** las reservas del negocio, sin importar qué cliente las creó.

### `GET /api/v1/businesses/{businessId}/bookings?from=&to=&status=&page=0&size=20`
- **Orden:** de la más reciente a la más antigua por fecha de inicio (desempate por id).
- `from` / `to` (opcionales, `yyyy-MM-dd`): **inclusivos**, sobre el día de inicio de la reserva en hora de Bogotá (una reserva de 19:00 a 20:00 cuenta para su día local aunque en UTC ya sea el siguiente). Formato inválido o `from` posterior a `to` → 400.
- `status` (opcional): `CONFIRMADA`, `CANCELADA` o `COMPLETADA`, sin distinguir mayúsculas; otro valor → 400.
- `page` desde 0; `size` defecto 20, **tope 50** (uno mayor se recorta); `page < 0`, `size < 1` o no numérico → 400.

```json
{ "items": [ { "id": "…", "status": "CONFIRMADA", "clientId": "…", "clientName": "Ana Cliente",
               "clientEmail": "ana@example.com", "serviceId": "…", "serviceName": "Corte",
               "resourceId": "…", "resourceName": "Sala 1", "date": "2026-10-19", "startTime": "10:00", "endTime": "11:00",
               "priceCop": 25000, "cancelReason": null, "cancelledAt": null, "createdAt": "…" } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "message": null }
```
Cada reserva muestra fecha, horas, servicio, recurso y **todos los datos del cliente que la reserva conserva**: `clientId`, `clientName` y `clientEmail`. Nombre y correo son copias del momento de reservar (no cambian si el cliente luego los edita ni si borra su cuenta; en ese caso `clientId` pasa a `null`). *Decisión del equipo (2026-10-10):* se incluyen aunque la HU solo pida el nombre, porque pueden servir para contactar al cliente o para futuras funciones. **No incluye el celular**: la reserva no lo guarda (si se necesita habría que copiarlo al reservar, con otra migración). Al ser datos personales, solo los ve el proveedor dueño del negocio. Sin reservas: lista vacía y `message` *"No hay reservas registradas"*; con filtros sin resultados: *"No hay reservas con los filtros indicados"*.

> **Cambio de BD:** la migración `V11__add_client_name_to_bookings.sql` añade `bookings.client_name` y rellena las filas existentes con el nombre actual del usuario (o su correo si ya no existe). Se probó contra una base vacía; con datos previos conviene revisarla antes de aplicarla en una base con reservas.

## HU-25 — Cancelar reserva como cliente (implementado)

### `POST /api/v1/bookings/{bookingId}/cancellation`

Solo el rol **CLIENTE** y solo sobre **sus** reservas (proveedor o administrador: 403; reserva de otro cliente: 403; sin sesión: 401; reserva inexistente: 404; id que no es UUID: 400). Cuerpo **opcional**:

```json
{ "reason": "No puedo asistir" }
```
`reason`: texto libre de hasta 500 caracteres (400 si lo supera); en blanco o ausente se guarda como ausente. Respuesta `200` con la reserva ya cancelada (mismo formato de HU-23):

```json
{ "id": "…", "status": "CANCELADA", "cancelOrigin": "CLIENTE", "cancelReason": "No puedo asistir",
  "cancelledAt": "2026-10-10T15:30:00Z", "…": "…" }
```
- **Estado + origen** (regla acordada en el plan §6): la reserva pasa a `CANCELADA` con `cancelOrigin = CLIENTE`. El campo `cancelOrigin` también aparece en HU-23 y HU-24 (nulo en reservas no canceladas; los orígenes posibles son `CLIENTE`, `PROVEEDOR`, `ELIMINACION_CUENTA`, `RECURSO_NO_DISPONIBLE`, `SERVICIO_NO_DISPONIBLE`; HU-26 y HU-28 usarán los demás).
- **Libera el horario:** la restricción anti-overbooking solo cuenta reservas CONFIRMADAS, así que el horario vuelve a aparecer en HU-20 y otro cliente puede reservarlo de inmediato.
- **Plazo (regla fija de la plataforma):** solo se puede cancelar con **al menos 1 hora de antelación** al inicio (exactamente 1 hora sí se permite). Las cancelaciones que no decide el cliente (proveedor, eliminación de cuenta) no están sujetas a esta regla.
- **Notificación al proveedor:** las notificaciones están fuera de alcance (plan, decisión 5). El aviso del criterio de aceptación queda como evento de auditoría `CANCELACION_RESERVA` (negocio, servicio y recurso afectados) y el proveedor ve la reserva como `CANCELADA` en HU-24.

| Código | Cuándo |
|---|---|
| 409 | Ya está cancelada (*"La reserva ya está cancelada"*), ya fue completada, o faltan menos de 1 hora (*"…al menos 1 hora de antelación…"*). Dos cancelaciones simultáneas: una da 200 y la otra 409 (se bloquea la fila de la reserva) |

> **Cambio de BD:** migración `V12__add_cancel_origin_to_bookings.sql` (`cancel_origin` con CHECK). Las reservas canceladas antes de la migración quedan con origen nulo.
