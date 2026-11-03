# Contratos de la API — Sprint 3

**Para Calidad (casos de prueba del 2 al 6 de noviembre).** Implementado y probado en el código el 2026-11-02. La lista completa de endpoints con su rol sale del código en [`referencia-api-sprint-2.md`](referencia-api-sprint-2.md) (48 endpoints) y cada operación está documentada en Swagger (`/swagger-ui.html`). Formato de error: el mismo de siempre (`status`, `error`, `message`, `fields`, `traceId`; ver [`errores-api-sprint-2.md`](errores-api-sprint-2.md)).

Reglas comunes a todo lo de esta página: sin sesión → `401`; rol distinto del indicado → `403`; recurso, servicio o negocio de **otro** proveedor → `403` sin cambiar nada; id inexistente → `404`; id mal formado → `400`. Fechas `yyyy-MM-dd` y horas `HH:mm` en **hora de Bogotá**.

## 1. HU pendientes del Sprint 2

### HU-07 — Editar información del negocio · `PATCH /api/v1/businesses/{businessId}` · PROVEEDOR dueño

Edición **parcial**: se envía solo lo que cambia. Campo ausente o `null` = no cambia; en los opcionales, cadena vacía = **se borra**.

```json
{"name": "Barbería Central", "description": "Cortes clásicos", "address": "Calle 10 # 43-12",
 "businessType": "BELLEZA", "phone": "6045551234"}
```

| Campo | Regla | Error |
|---|---|---|
| `name` | obligatorio: no puede quedar vacío; máx. 150 | 400 «El nombre del negocio es obligatorio» |
| `description` | máx. 500 | 400 |
| `address` | máx. 200 | 400 |
| `businessType` | `SALUD`, `BELLEZA`, `BIENESTAR`, `DEPORTE`, `EDUCACION`, `CONSULTORIA`, `ENTRETENIMIENTO`, `OTRO` (sin distinguir mayúsculas) | 400 «El tipo de negocio no es válido…» (p. ej. `Panadería`, `123`) |
| `phone` | 10 dígitos: celular `3XXXXXXXXX` o fijo `60XXXXXXXX` | 400 «El teléfono no es válido…» (p. ej. `abcde12345`, `12345`, `123`) |

Respuesta `200`: `{"business": {...todos los datos...}, "message": "La información del negocio fue actualizada"}`. Si un campo es inválido **no se guarda nada**. El cliente ve los datos nuevos en `GET /api/v1/businesses/{businessId}` (detalle del catálogo, ahora con `description`, `address`, `businessType`, `phone`). Auditoría: `EDICION_NEGOCIO` con los campos cambiados.

### HU-10 — Editar servicio · `PATCH /api/v1/services/{serviceId}` · PROVEEDOR dueño

Parcial: `name`, `description` (vacía = se borra), `durationMinutes` (1–1440), `priceCop` (0–1.000.000.000; **0 es válido**). Nombre repetido en el negocio → `409`. **Las reservas ya hechas conservan su precio y su horario** (el precio se copió al reservar). `200` con el servicio. Auditoría: `EDICION_SERVICIO` («precio 50000 -> 60000 COP»).

### HU-11 — Desactivar servicio · `POST /api/v1/services/{serviceId}/deactivation` · PROVEEDOR dueño

Cuerpo opcional `{"confirm": true}`. Mismo patrón que HU-16 (recursos):

| Situación | Respuesta |
|---|---|
| Sin reservas futuras | `200` `{"serviceId","name","active":false,"cancelledBookings":0}` |
| Con reservas futuras y sin `confirm` | `409 CONFIRMATION_REQUIRED`, `fields.affectedBookings = "3"`; **no cambia nada** |
| Con `confirm: true` | `200`; las reservas futuras confirmadas quedan `CANCELADA`, `cancelOrigin = SERVICIO_NO_DISPONIBLE`, motivo «Servicio no disponible: el proveedor lo desactivó» (sin la regla de 1 hora) |
| Ya inactivo | `200` sin cambios |

Después: reservar ese servicio → `404` «El servicio no está disponible»; desaparece del catálogo y de la disponibilidad. Reservas pasadas: intactas.

### HU-12 — Reactivar servicio · `POST /api/v1/services/{serviceId}/reactivation` · PROVEEDOR dueño

`200` `{"id","name","active":true}`. Es el **mismo** servicio con sus datos (no se crea otro); idempotente. Las reservas canceladas al desactivarlo **siguen canceladas**.

### HU-15 — Editar recurso · `PATCH /api/v1/resources/{resourceId}` · PROVEEDOR dueño

Parcial: `name` (no puede quedar vacío, único en el negocio sin distinguir mayúsculas → `409`), `type` (`SALA`, `EQUIPO`, `PERSONAL`). Un `businessId` en el cuerpo **se ignora** (el recurso no cambia de negocio). Recurso inexistente → `404` «El recurso solicitado no existe». Auditoría: `EDICION_RECURSO`.

### HU-21 — Bloquear un horario puntual (cierres)

| Operación | Ruta |
|---|---|
| Cerrar | `POST /api/v1/resources/{resourceId}/closures` |
| Listar los que aún no terminan | `GET /api/v1/resources/{resourceId}/closures` |
| Eliminar uno que no ha empezado | `DELETE /api/v1/resources/{resourceId}/closures/{closureId}` → `204` |

Cuerpo de cierre: **o** `{"date": "2026-12-08"}` (día completo) **o** `{"start": "2026-11-20 14:00", "end": "2026-11-20 18:00"}` (también se acepta `T` en lugar del espacio), más `reason` (opcional, máx. 300) y `confirm`.

| Caso | Respuesta |
|---|---|
| Sin reservas en el periodo | `201` `{"closure": {...}, "cancelledBookings": 0, "message": "El recurso quedó cerrado en ese periodo"}` |
| Con reservas confirmadas en el periodo y sin `confirm` | `409 CONFIRMATION_REQUIRED` con `fields.affectedBookings`; no cambia nada |
| Con `confirm: true` | `201`; se cancelan solo las reservas que se cruzan (`cancelOrigin = RECURSO_NO_DISPONIBLE`) |
| Fin ≤ inicio (`14:00→10:00`, `09:00→09:00`) o formato inválido, o `date` junto con `start/end` | `400` «El periodo de cierre no es válido…» |
| Fecha anterior a hoy (`2026-09-01`) | `400` «La fecha del cierre debe ser igual o posterior a la fecha actual» |
| Se cruza con otro cierre del recurso | `409` «Ya existe un cierre programado que se cruza con ese periodo» (un borde compartido sí se permite) |
| Más de 366 días | `400` |
| Eliminar un cierre que ya empezó | `409` |

Efecto: el **horario semanal no cambia** (HU-19); el cierre se descuenta de la disponibilidad pública (HU-20) y una reserva dentro del cierre se rechaza con `409` «…queda fuera del horario de atención». Los otros recursos no se afectan. Auditoría: `CIERRE_RECURSO` (con la cantidad de reservas canceladas) y `ELIMINACION_CIERRE_RECURSO`.

### HU-27 — Completar reserva · `POST /api/v1/bookings/{bookingId}/completion` · PROVEEDOR dueño

Sin cuerpo. Exige reserva `CONFIRMADA` **cuya hora de fin ya pasó**. `200` con la reserva en `COMPLETADA`. Cancelada, ya completada o aún sin terminar → `409`. El cliente la ve en `GET /api/v1/bookings/me?status=COMPLETADA`. Auditoría: `FINALIZACION_RESERVA`. **La calificación o reseña no existe** (no hay HU que la defina; supuesto D5).

## 2. HU del Sprint 3 — reportes (módulo `report`)

Rutas `GET /api/v1/businesses/{businessId}/reports/...` (PROVEEDOR **dueño**; un cliente u otro proveedor → `403`) y `GET /api/v1/admin/reports/platform-summary` (ADMINISTRADOR con MFA).

**Periodo:** `from` y `to` (`yyyy-MM-dd`, inclusivos, por el **día de inicio** de la reserva en Bogotá). Sin fechas: los últimos 30 días hasta hoy; con una sola, la otra se completa a 30 días. Inicio posterior al fin, formato inválido o más de 366 días → `400`. Sin datos: `200` con listas vacías o ceros y `message: "No hay reservas en el periodo seleccionado"`.

**Ocupación (supuesto D1, a confirmar):** minutos reservados (reservas **confirmadas + completadas**) ÷ minutos disponibles (horario semanal **vigente** de cada recurso **activo**, menos sus cierres de HU-21) × 100, con un decimal. Si no hay minutos disponibles, `occupancyPercent` es `null`.

| HU | Ruta | Qué devuelve |
|---|---|---|
| HU-29 | `.../reports/resource-occupancy` | `resources[]`: `resourceId`, `name`, `type`, `availableMinutes`, `bookedMinutes`, `occupancyPercent` (de más a menos usado) + totales del negocio |
| HU-30 | `.../reports/occupancy?groupBy=DAY\|WEEK` | `periods[]` por día o por semana de lunes a domingo (recortada al periodo), `highestDemand` y `lowestDemand` (entre los periodos con atención) y el porcentaje total |
| HU-31 | `.../reports/bookings-by-status` | `confirmed`, `cancelled`, `completed`, `total` y `cancelledByOrigin` (`CLIENTE`, `PROVEEDOR`, `RECURSO_NO_DISPONIBLE`, …) |
| HU-32 | `.../reports/top-services?limit=10` | `services[]`: `rank`, `serviceId`, `serviceName` (nombre **vigente**; si se eliminó, el guardado + «(servicio eliminado)»), `bookings` (confirmadas + completadas), `cancelled`. `limit` 1–50 (si no, `400`) |
| HU-33 | `.../reports/booking-value` | En COP según el **precio guardado en cada reserva**: `completedValue`, `confirmedValue`, `totalValue` (= completadas + confirmadas), `cancelledValue` aparte y una nota: es una referencia, **no hay pagos** |
| HU-34 | `/api/v1/admin/reports/platform-summary` | `activeBusinesses` (proveedor con cuenta habilitada), reservas por estado y ocupación general de todos los recursos activos |

## 3. Supuestos que Calidad/PO deben confirmar

Están numerados en [`plan-de-trabajo-sprint-3.md`](../sprint-3/plan-de-trabajo-sprint-3.md) §3: definición de ocupación (D1), periodo por defecto y tope (D3), completar solo tras la hora de fin (D4), sin calificación (D5), PATCH parcial con «vacío = borrar» (D6), lista de tipos de negocio (D7), formato del teléfono (D8), cierre por día o por rango (D10), aviso al cliente = auditoría (D12) y «negocio activo» (D13).
