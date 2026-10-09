# Trazabilidad del Sprint 2: HU → Regla → API → Componente → Tabla → Prueba

> **Generado automáticamente** por `generar.py` desde `backlog_data.py` (HUS). Los endpoints son una **propuesta a validar en el Día 1**; los IDs `CP-*` son provisionales hasta que Calidad asigne los definitivos.

| HU | Pts | Tier | Módulo (paquete) | API propuesta | Tabla(s) | Reglas | Casos |
|---|---:|---:|---|---|---|---:|---:|
| HU-08 | 3 | 1 | provider | `PUT /api/v1/businesses/{businessId}/booking-lead-time`<br>`GET /api/v1/businesses/{businessId}` | businesses | 4 | 8 |
| HU-09 | 5 | 1 | service | `POST /api/v1/businesses/{businessId}/services`<br>`GET /api/v1/businesses/{businessId}/services` | services | 5 | 10 |
| HU-13 | 3 | 1 | service (catálogo) | `GET /api/v1/businesses?page=&size=`<br>`GET /api/v1/businesses/{businessId}` | — | 4 | 7 |
| HU-14 | 5 | 1 | resource | `POST /api/v1/businesses/{businessId}/resources`<br>`GET /api/v1/businesses/{businessId}/resources` | resources | 4 | 7 |
| HU-18 | 5 | 1 | service + resource | `PUT /api/v1/services/{serviceId}/resources`<br>`GET /api/v1/services/{serviceId}/resources` | service_resources | 4 | 6 |
| HU-19 | 3 | 1 | resource | `PUT /api/v1/resources/{resourceId}/availability`<br>`PUT /api/v1/resources/{resourceId}/availability/{dayOfWeek}`<br>`GET /api/v1/resources/{resourceId}/availability` | resource_availability | 5 | 9 |
| HU-20 | 5 | 1 | reservation (disponibilidad) | `GET /api/v1/services/{serviceId}/availability?date=yyyy-MM-dd` | — | 5 | 10 |
| HU-22 | 8 | 1 | reservation | `POST /api/v1/bookings` | bookings | 6 | 11 |
| HU-23 | 5 | 1 | reservation | `GET /api/v1/bookings/me?status=&page=&size=` | — | 4 | 6 |
| HU-24 | 5 | 1 | reservation | `GET /api/v1/businesses/{businessId}/bookings?from=&to=&status=&page=&size=` | — | 3 | 6 |
| HU-25 | 5 | 1 | reservation | `POST /api/v1/bookings/{bookingId}/cancellation` | — | 4 | 6 |
| HU-16 | 5 | 2 | resource | `POST /api/v1/resources/{resourceId}/deactivation` | — | 4 | 7 |
| HU-17 | 3 | 2 | resource | `POST /api/v1/resources/{resourceId}/reactivation` | — | 3 | 5 |
| HU-26 | 5 | 2 | reservation | `POST /api/v1/bookings/{bookingId}/provider-cancellation` | — | 4 | 6 |
| HU-28 | 5 | 2 | identity + reservation | `DELETE /api/v1/users/{userId}` | — | 5 | 6 |

## HU 08 - Definir antelación mínima de reserva (Azure 73)

- **Módulo:** provider · **Responsable:** Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · **Depende de:** HU-03
- **Tablas/datos:** businesses (+ min_advance_hours INT NOT NULL DEFAULT 1)
- **API propuesta:** `PUT /api/v1/businesses/{businessId}/booking-lead-time  {hours}`; `GET /api/v1/businesses/{businessId}`
- **Reglas de negocio:**
  - Valor por defecto 1 hora; cada negocio tiene el suyo; unidad: horas enteras >= 1 (a confirmar)
  - Solo el dueño del negocio (ajeno 403, Cliente 403, sin sesión 401)
  - Valores inválidos (0, -2, abc) -> 400 y se conserva el anterior
  - No afecta reservas ya confirmadas; HU-22 lo exige en las nuevas
- **Casos de prueba:**
  - CP-HU08-01 valor por defecto
  - CP-HU08-02 definir 2 h
  - CP-HU08-03 inválidos 0/-2/abc
  - CP-HU08-04 negocio ajeno 403
  - CP-HU08-05 Cliente 403
  - CP-HU08-06 sin sesión 401
  - CP-HU08-07 reservas previas intactas
  - CP-HU08-08 se aplica en HU-22

## HU 09 - Crear servicio (Azure 74)

- **Módulo:** service · **Responsable:** Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · **Depende de:** HU-03
- **Tablas/datos:** services (id, business_id, name, description, duration_minutes, price_cop, active, created_by, created_at, updated_at; UNIQUE(business_id, lower(name)))
- **API propuesta:** `POST /api/v1/businesses/{businessId}/services`; `GET /api/v1/businesses/{businessId}/services`
- **Reglas de negocio:**
  - Obligatorios: nombre, duración (entero > 0, en minutos) y precio en COP (>= 0; 0 = gratuito)
  - El servicio nace ACTIVO; nombre único por negocio sin distinguir mayúsculas (409)
  - Solo el dueño del negocio; negocio ajeno 403, Cliente 403, sin sesión 401; el negocio sale del path, nunca del body
  - Sin recursos asignados no se puede reservar (mensaje de 'sin disponibilidad')
  - Auditar quién y cuándo lo creó
- **Casos de prueba:**
  - CP-HU09-01 creación válida (201, activo)
  - CP-HU09-02 precio 0 (gratuito)
  - CP-HU09-03 falta nombre/duración/precio (400 por campo)
  - CP-HU09-04 duración 0, -30, abc
  - CP-HU09-05 precio -5000, abc
  - CP-HU09-06 nombre repetido (409)
  - CP-HU09-07 negocio ajeno 403
  - CP-HU09-08 Cliente 403
  - CP-HU09-09 sin sesión 401
  - CP-HU09-10 registro de auditoría

## HU 13 - Consultar negocios y servicios (Azure 78)

- **Módulo:** service (catálogo) · **Responsable:** Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · **Depende de:** HU-09
- **Tablas/datos:** (sin tablas nuevas; consultas sobre businesses y services)
- **API propuesta:** `GET /api/v1/businesses?page=&size=`; `GET /api/v1/businesses/{businessId}`
- **Reglas de negocio:**
  - Solo usuarios autenticados; HU-20 también exige sesión (decidido 2026-10-08)
  - Solo negocios con proveedor registrado; cada servicio muestra nombre, descripción, duración y precio
  - Solo servicios ACTIVOS; solo lectura; paginación con tope máximo
  - Catálogo vacío -> lista vacía + mensaje; negocio inexistente -> 404; sin sesión 401
- **Casos de prueba:**
  - CP-HU13-01 listar negocios
  - CP-HU13-02 detalle con servicios
  - CP-HU13-03 catálogo vacío
  - CP-HU13-04 negocio sin servicios
  - CP-HU13-05 negocio inexistente 404
  - CP-HU13-06 sin sesión 401
  - CP-HU13-07 servicio inactivo no aparece

## HU 14 - Registrar recurso (Azure 79)

- **Módulo:** resource · **Responsable:** Dev C (Santiago Rendón) · Recursos, horarios, cancelaciones, CI/CD y despliegue · **Depende de:** HU-03
- **Tablas/datos:** resources (id, business_id, name, type, active, created_at, updated_at; UNIQUE(business_id, lower(name)))
- **API propuesta:** `POST /api/v1/businesses/{businessId}/resources`; `GET /api/v1/businesses/{businessId}/resources`
- **Reglas de negocio:**
  - Solo Proveedor autenticado (Cliente 403); nombre y tipo obligatorios
  - Tipo: lista fija SALA | EQUIPO | PERSONAL (a confirmar vs. 'consultorio, sala, cancha, puesto' de la descripción)
  - Nombre único dentro del negocio (409)
  - El recurso queda SIEMPRE en el negocio del proveedor autenticado; un negocio enviado en el body se ignora
- **Casos de prueba:**
  - CP-HU14-01 registro válido
  - CP-HU14-02 sin nombre
  - CP-HU14-03 sin tipo
  - CP-HU14-04 nombre duplicado 409
  - CP-HU14-05 negocio ajeno en body se ignora
  - CP-HU14-06 Cliente 403
  - CP-HU14-07 sin sesión 401

## HU 18 - Asignar recursos a un servicio (Azure 83)

- **Módulo:** service + resource · **Responsable:** Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · **Depende de:** HU-09, HU-14
- **Tablas/datos:** service_resources (service_id, resource_id, PK compuesta, FK ON DELETE CASCADE)
- **API propuesta:** `PUT /api/v1/services/{serviceId}/resources  {resourceIds:[...]}`; `GET /api/v1/services/{serviceId}/resources`
- **Reglas de negocio:**
  - Servicio y recursos deben pertenecer al negocio del proveedor autenticado
  - Recurso ajeno o inexistente -> se rechaza TODA la operación (atómica), sin asignar nada
  - PUT reemplaza el conjunto (idempotente); permite varios recursos por servicio
  - Es requisito de HU-20 (disponibilidad) y HU-22 (reserva)
- **Casos de prueba:**
  - CP-HU18-01 asignar un recurso
  - CP-HU18-02 asignar varios
  - CP-HU18-03 recurso de otro negocio
  - CP-HU18-04 recurso inexistente
  - CP-HU18-05 servicio ajeno 403
  - CP-HU18-06 Cliente 403

## HU 19 - Definir horarios de atención de un recurso (Azure 84)

- **Módulo:** resource · **Responsable:** Dev C (Santiago Rendón) · Recursos, horarios, cancelaciones, CI/CD y despliegue · **Depende de:** HU-14
- **Tablas/datos:** resource_availability (id, resource_id, day_of_week 1-7, start_time, end_time; CHECK start<end; sin traslapes por día)
- **API propuesta:** `PUT /api/v1/resources/{resourceId}/availability`; `PUT /api/v1/resources/{resourceId}/availability/{dayOfWeek}`; `GET /api/v1/resources/{resourceId}/availability`
- **Reglas de negocio:**
  - Rangos HH:mm con inicio < fin (14:00-10:00, 09:00-09:00, 25:00-26:00, ab:cd -> 400)
  - Rangos del mismo día no pueden traslaparse; día sin rangos = no disponible
  - Editar un día no cambia los demás días ni otros recursos
  - Recurso ajeno 403, Cliente 403, sin sesión 401; auditar quién y cuándo modificó
  - Zona horaria de negocio: America/Bogota; sin horario definido no se puede reservar
- **Casos de prueba:**
  - CP-HU19-01 horario completo
  - CP-HU19-02 editar solo lunes
  - CP-HU19-03 domingo no disponible
  - CP-HU19-04 rangos inválidos (4 ejemplos)
  - CP-HU19-05 traslape -> 400
  - CP-HU19-06 recurso ajeno 403
  - CP-HU19-07 Cliente 403
  - CP-HU19-08 sin sesión 401
  - CP-HU19-09 auditoría

## HU 20 - Consultar disponibilidad de un servicio (Azure 85)

- **Módulo:** reservation (disponibilidad) · **Responsable:** Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · **Depende de:** HU-18, HU-19
- **Tablas/datos:** (sin tablas nuevas; consulta sobre resource_availability, service_resources y bookings)
- **API propuesta:** `GET /api/v1/services/{serviceId}/availability?date=yyyy-MM-dd   (cliente con sesión; sin token 401)`
- **Reglas de negocio:**
  - Fecha ISO yyyy-MM-dd; inválida (32/13/2026, ab/cd/efgh, 2026-99-99) -> 400; pasada -> 400; sin fecha = hoy
  - Horarios = disponibilidad de recursos activos asignados - reservas CONFIRMADAS - (ahora + antelación mínima)
  - Sin disponibilidad o todo reservado -> mensaje 'no hay horarios'; servicio inexistente/inactivo -> 404 'no disponible'
  - Requiere cliente con sesión (AC de HU-20: "un cliente consulta"); los cambios de horario del recurso se ven en la consulta siguiente
  - Rendimiento: índices por (resource_id, start_at) y paginación/tope de rango de fechas
- **Casos de prueba:**
  - CP-HU20-01 horarios libres
  - CP-HU20-02 excluye reservados
  - CP-HU20-03 día sin disponibilidad
  - CP-HU20-04 todo reservado
  - CP-HU20-05 fecha pasada
  - CP-HU20-06 fecha inválida (3 ejemplos)
  - CP-HU20-07 servicio inexistente
  - CP-HU20-08 sin fecha = hoy
  - CP-HU20-09 sin sesión permitido
  - CP-HU20-10 dos consultas simultáneas

## HU 22 - Crear reserva (Azure 87)

- **Módulo:** reservation · **Responsable:** Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · **Depende de:** HU-08, HU-09, HU-14, HU-18, HU-19, HU-20
- **Tablas/datos:** bookings (id, client_id, client_email, service_id, service_name, business_name, resource_id, start_at, end_at, status, cancel_reason, price_cop, created_at, cancelled_at) + EXCLUDE USING gist (anti-traslape por recurso) [requiere btree_gist]
- **API propuesta:** `POST /api/v1/bookings  {serviceId, startAt[, resourceId]}`
- **Reglas de negocio:**
  - Solo rol Cliente; estado inicial CONFIRMADA (automática); la respuesta incluye el id
  - Fin = inicio + duración del servicio (el cliente no envía hora fin); validar rango y fecha obligatoria
  - Servicio activo, con recurso activo asignado y dentro del horario del recurso
  - Inicio >= ahora + antelación mínima del negocio (1 h por defecto, HU-08)
  - Anti-overbooking por RECURSO: restricción EXCLUDE en BD + 409 'horario no disponible' ante concurrencia
  - Guarda el precio vigente y datos de historial (snapshots) para sobrevivir a ediciones/eliminaciones
- **Casos de prueba:**
  - CP-HU22-01 reserva válida (CONFIRMADA + id)
  - CP-HU22-02 horario ocupado 409
  - CP-HU22-03 sin fecha 400
  - CP-HU22-04 fin <= inicio / rango inválido
  - CP-HU22-05 sin sesión 401
  - CP-HU22-06 proveedor no puede reservar 403
  - CP-HU22-07 menos de la antelación mínima
  - CP-HU22-08 fuera del horario del recurso
  - CP-HU22-09 servicio/recurso inactivo
  - CP-HU22-10 concurrencia: 2 reservas simultáneas, 1 gana
  - CP-HU22-11 snapshot de precio

## HU 23 - Consultar mis reservas (Azure 88)

- **Módulo:** reservation · **Responsable:** Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · **Depende de:** HU-22
- **Tablas/datos:** (sin tablas nuevas)
- **API propuesta:** `GET /api/v1/bookings/me?status=&page=&size=`
- **Reglas de negocio:**
  - Solo las reservas del Cliente autenticado; orden por fecha de más reciente a más antigua
  - Cada reserva: fecha, hora inicio/fin, servicio, estado y motivo de cancelación si aplica
  - Lista vacía -> mensaje 'no tiene reservas'; no existe ruta para ver reservas de otro usuario (403/404)
  - Sin sesión 401
- **Casos de prueba:**
  - CP-HU23-01 listado con reservas
  - CP-HU23-02 sin reservas
  - CP-HU23-03 intento de ver ajenas
  - CP-HU23-04 sin sesión 401
  - CP-HU23-05 orden descendente
  - CP-HU23-06 muestra motivo de cancelación

## HU 24 - Consultar reservas del negocio (Azure 89)

- **Módulo:** reservation · **Responsable:** Dev C (Santiago Rendón) · Recursos, horarios, cancelaciones, CI/CD y despliegue · **Depende de:** HU-22
- **Tablas/datos:** (sin tablas nuevas)
- **API propuesta:** `GET /api/v1/businesses/{businessId}/bookings?from=&to=&status=&page=&size=`
- **Reglas de negocio:**
  - Solo el Proveedor dueño del negocio; negocio ajeno 403; Cliente 403; sin sesión 401
  - Muestra todas las reservas de sus servicios: fecha, horas, servicio y nombre del Cliente
  - Orden descendente por fecha; lista vacía -> mensaje 'no hay reservas'
- **Casos de prueba:**
  - CP-HU24-01 listado del negocio
  - CP-HU24-02 sin reservas
  - CP-HU24-03 negocio ajeno 403
  - CP-HU24-04 Cliente 403
  - CP-HU24-05 sin sesión 401
  - CP-HU24-06 filtros de fecha/estado

## HU 25 - Cancelar reserva como cliente (Azure 90)

- **Módulo:** reservation · **Responsable:** Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · **Depende de:** HU-23
- **Tablas/datos:** (usa bookings: status, cancel_reason, cancelled_at)
- **API propuesta:** `POST /api/v1/bookings/{bookingId}/cancellation`
- **Reglas de negocio:**
  - Solo el Cliente dueño de la reserva CONFIRMADA; con al menos 1 hora de antelación (regla fija de plataforma)
  - Estado -> CANCELADA con motivo CLIENTE; el horario queda libre (el EXCLUDE solo cuenta CONFIRMADA)
  - Fuera de plazo o ya cancelada -> 409 con mensaje claro; ajena -> 403
  - El aviso al proveedor del AC queda como evento de auditoría (notificaciones están fuera de alcance)
- **Casos de prueba:**
  - CP-HU25-01 cancelación exitosa
  - CP-HU25-02 con menos de 1 h
  - CP-HU25-03 reserva ajena 403
  - CP-HU25-04 ya cancelada 409
  - CP-HU25-05 el horario vuelve a estar disponible (HU-20)
  - CP-HU25-06 sin sesión 401

## HU 16 - Desactivar recurso (Azure 81)

- **Módulo:** resource · **Responsable:** Dev C (Santiago Rendón) · Recursos, horarios, cancelaciones, CI/CD y despliegue · **Depende de:** HU-14, HU-22, HU-24
- **Tablas/datos:** (usa resources.active y bookings)
- **API propuesta:** `POST /api/v1/resources/{resourceId}/deactivation  {confirm:boolean}`
- **Reglas de negocio:**
  - Sin reservas futuras -> desactiva directo
  - Con reservas futuras y sin confirm -> 409 CONFIRMATION_REQUIRED con `affectedBookings` y el recurso sigue activo
  - Con confirm=true -> desactiva y cancela las futuras con motivo RECURSO_NO_DISPONIBLE (sin regla de 1 h); el historial se conserva
  - Una sola transacción; recurso ajeno 403; patrón de confirmación reutilizable en HU-11/21/28
- **Casos de prueba:**
  - CP-HU16-01 sin reservas futuras
  - CP-HU16-02 con futuras pide confirmación (cuenta)
  - CP-HU16-03 confirma y cancela
  - CP-HU16-04 no confirma, sin cambios
  - CP-HU16-05 recurso ajeno 403
  - CP-HU16-06 atomicidad ante falla
  - CP-HU16-07 el recurso deja de ofrecer horarios

## HU 17 - Reactivar recurso (Azure 82)

- **Módulo:** resource · **Responsable:** Dev C (Santiago Rendón) · Recursos, horarios, cancelaciones, CI/CD y despliegue · **Depende de:** HU-16
- **Tablas/datos:** (usa resources.active)
- **API propuesta:** `POST /api/v1/resources/{resourceId}/reactivation`
- **Reglas de negocio:**
  - Reactiva el MISMO recurso (sin duplicar) y vuelve a recibir reservas
  - Las reservas canceladas por la desactivación permanecen canceladas
  - Recurso ajeno 403; ya activo -> respuesta idempotente
- **Casos de prueba:**
  - CP-HU17-01 reactivar
  - CP-HU17-02 sin duplicar
  - CP-HU17-03 recurso ajeno 403
  - CP-HU17-04 reservas canceladas siguen canceladas
  - CP-HU17-05 vuelve a aparecer en disponibilidad

## HU 26 - Cancelar reserva como proveedor (Azure 91)

- **Módulo:** reservation · **Responsable:** Dev C (Santiago Rendón) · Recursos, horarios, cancelaciones, CI/CD y despliegue · **Depende de:** HU-24
- **Tablas/datos:** (usa bookings: cancel_reason, cancel_note)
- **API propuesta:** `POST /api/v1/bookings/{bookingId}/provider-cancellation  {reason}`
- **Reglas de negocio:**
  - Solo el Proveedor dueño del negocio de la reserva; motivo (texto) obligatorio
  - Estado CANCELADA con motivo PROVEEDOR + texto; sin regla de 1 h (a confirmar)
  - El horario queda libre; el Cliente verá el motivo en HU-23; reserva ajena 403
  - El aviso al cliente queda como evento de auditoría (notificaciones fuera de alcance)
- **Casos de prueba:**
  - CP-HU26-01 cancelación con motivo
  - CP-HU26-02 sin motivo 400
  - CP-HU26-03 reserva de otro negocio 403
  - CP-HU26-04 Cliente 403
  - CP-HU26-05 el cliente ve el motivo
  - CP-HU26-06 horario liberado

## HU 28 - Cancelar reservas futuras al eliminar un usuario (Azure 92)

- **Módulo:** identity + reservation · **Responsable:** Dev A (Simon Betancur) · Identidad, seguridad y calidad · **Depende de:** HU-05, HU-22, HU-26
- **Tablas/datos:** (usa bookings; FKs hacia users/services/resources con SET NULL + snapshots)
- **API propuesta:** `DELETE /api/v1/users/{userId}  (extiende HU-05)`
- **Reglas de negocio:**
  - En la MISMA transacción: reservas futuras del usuario (como cliente o dueño del negocio) -> CANCELADA con motivo ELIMINACION_CUENTA (sin regla de 1 h)
  - Las reservas pasadas se conservan como historial (por eso bookings guarda snapshots y no cae en cascada)
  - Servicios/recursos del proveedor eliminado dejan de ser reservables
  - Autoeliminación de cuenta (cliente/proveedor): decisión pendiente; hoy solo Administrador (HU-05)
  - Notificaciones fuera de alcance -> evento de auditoría
- **Casos de prueba:**
  - CP-HU28-01 cliente con reservas futuras
  - CP-HU28-02 proveedor con reservas futuras
  - CP-HU28-03 reservas pasadas se conservan
  - CP-HU28-04 admin elimina usuario
  - CP-HU28-05 atomicidad
  - CP-HU28-06 admin no puede eliminar admin (HU-05)

