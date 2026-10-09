# -*- coding: utf-8 -*-
"""
DATOS del backlog Scrum del Sprint 2 (Azure DevOps, proceso AGILE: User Story / Task).

FUENTE ÚNICA. Edita SOLO este archivo y corre `python generar.py`. No edites a mano los CSV
ni `tareas-sprint-2.md`: se regeneran.

POLÍTICA (decidida por el usuario el 2026-10-07): Azure lleva POCAS tareas y de nivel
ARQUITECTÓNICO — una por componente o parte desarrollada de la aplicación. NO se crean tareas
para correcciones/bugs, refactors, ajustes, pruebas, documentación suelta, estudio ni higiene:
eso va en el commit/PR (`Fixes #n`) o en `PENDIENTES`, un checklist interno que NO se sube.

Qué se sube a Azure
- Cada HU del sprint (HUS + COMPONENTES): 1 tarea "DATOS" si la HU crea tablas + 1 tarea "API"
  (reglas de negocio + endpoints). Las HU sin tablas nuevas llevan una sola tarea.
- Historias técnicas (TECH_STORIES) con sus tareas por componente (TAREAS_TECNICAS).
- Un componente NUEVO que no cubra nada de lo anterior: UNA llamada chg(...) en CAMBIOS, en un lote nuevo.

Convenciones
- Cada tarea tiene un código estable (HU09-API, SEG-01, CHG-006...). Va en el título "[CODIGO] ..." y en Tags.
- `lote` = archivo CSV a subir. Un lote ya subido a Azure NO se vuelve a importar (duplicaría).
  Lote vigente: L01 (línea base simplificada). Lo que se agregue después va en L02, L03, ...
- `ESTADO_LOTES`: "pendiente" (aún no subido) o "subido" (cámbialo cuando el usuario confirme).
- Horas: estimación nominal de esfuerzo (sin asistente de IA).
"""

SPRINT = "Sprint 2"
ITERATION_PATH = ""   # p. ej. "Reservas\\Sprint 2"; vacío = el valor por defecto del proyecto en Azure
AREA_PATH = ""

# Responsables. Claves A/B/C = los 3 integrantes de Arquitectura de Software.
# Rellena con el nombre exacto de Azure ("NOMBRE <correo>") cuando se conozca; vacío = sin asignar.
EQUIPO = {"A": "", "B": "", "C": "", "BD": "", "QA": ""}
ROL_TXT = {
    "A": "Dev A (Simon Betancur) · Identidad, seguridad y calidad",
    "B": "Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente",
    "C": "Dev C (Santiago Rendón) · Recursos, horarios, cancelaciones, CI/CD y despliegue",
    "BD": "Bases de Datos (Juan Sebastián Andraus, con apoyo de los 3 de Arquisoft)",
    "QA": "Equipo de Calidad",
}
# Reparto propuesto (docs/Sprint 1 ArquisuaveBD.docx, §2-9, "distribución propuesta"):
#   Simon = backend (estructura y seguridad) y puente backend<->BD; Juan Esteban = contratos/ADR y consultas de BD;
#   Santiago = calidad técnica (repo, ramas, pruebas, despliegue) y seguridad/pruebas de BD; Andraus = BD principal.
# "Juanes/Juanesgodu" en GitHub se infiere como Juan Esteban González Duque (confirmar).

# Módulo (paquete Java) donde vive cada HU; alimenta la matriz de trazabilidad.
MODULO = {
    "HU-08": "provider", "HU-09": "service", "HU-13": "service (catálogo)", "HU-14": "resource",
    "HU-16": "resource", "HU-17": "resource", "HU-18": "service + resource", "HU-19": "resource",
    "HU-20": "reservation (disponibilidad)", "HU-22": "reservation", "HU-23": "reservation",
    "HU-24": "reservation", "HU-25": "reservation", "HU-26": "reservation", "HU-28": "identity + reservation",
}

# Componente de cada HU: arma el título de sus tareas de Azure.
# (nombre del componente, qué hace la HU, tabla nueva, una_sola_tarea)
#   - Con tabla nueva y una_sola_tarea=False -> 2 tareas: "<comp>: modelo de datos y migración" + "<comp>: <resumen> (reglas y API REST)".
#   - Si no, una sola tarea "<comp>: <resumen> (reglas y API REST)".
COMPONENTES = {
    "HU-09": ("Módulo de servicios", "crear y listar los servicios del negocio", "services", False),
    "HU-13": ("Catálogo de negocios y servicios", "consulta paginada de negocios con sus servicios activos", "", True),
    "HU-14": ("Módulo de recursos", "registrar y listar los recursos del negocio", "resources", False),
    "HU-18": ("Asignación de recursos a servicios", "asignar y consultar los recursos de un servicio", "service_resources", False),
    "HU-19": ("Horarios de atención de recursos", "definir y consultar la disponibilidad semanal de un recurso", "resource_availability", False),
    "HU-20": ("Motor de disponibilidad", "consulta de los horarios libres de un servicio por fecha (cliente con sesión)", "", True),
    "HU-22": ("Módulo de reservas", "crear reserva con anti-overbooking", "bookings", False),
    "HU-23": ("Módulo de reservas", "consulta de las reservas del cliente", "", True),
    "HU-24": ("Módulo de reservas", "consulta de las reservas del negocio", "", True),
    "HU-25": ("Módulo de reservas", "cancelación de la reserva por el cliente", "", True),
    "HU-08": ("Configuración del negocio", "antelación mínima de reserva por negocio", "", True),
    "HU-16": ("Módulo de recursos", "desactivar un recurso con confirmación y cancelación de reservas futuras", "", True),
    "HU-17": ("Módulo de recursos", "reactivar un recurso", "", True),
    "HU-26": ("Módulo de reservas", "cancelación de la reserva por el proveedor", "", True),
    "HU-28": ("Identidad y reservas", "cancelación de reservas futuras al eliminar un usuario", "", True),
}

# ---------------------------------------------------------------------------
# Historias YA existentes en Azure (IDs reales, export del 2026-10-07)
# ---------------------------------------------------------------------------
STORY_IDS = {
    "HU-07": 72, "HU-08": 73, "HU-09": 74, "HU-10": 75, "HU-11": 76, "HU-12": 77,
    "HU-13": 78, "HU-14": 79, "HU-15": 80, "HU-16": 81, "HU-17": 82, "HU-18": 83,
    "HU-19": 84, "HU-20": 85, "HU-21": 86, "HU-22": 87, "HU-23": 88, "HU-24": 89,
    "HU-25": 90, "HU-26": 91, "HU-28": 92, "HU-27": 93,
}
STORY_TITLES = {
    "HU-07": "HU 07 - Editar información del negocio",
    "HU-08": "HU 08 - Definir antelación mínima de reserva",
    "HU-09": "HU 09 - Crear servicio",
    "HU-10": "HU 10 - Editar servicio",
    "HU-11": "HU 11 - Desactivar servicio",
    "HU-12": "HU 12 - Reactivar servicio",
    "HU-13": "HU 13 - Consultar negocios y servicios",
    "HU-14": "HU 14 - Registrar recurso",
    "HU-15": "HU 15 - Editar recurso",
    "HU-16": "HU 16 - Desactivar recurso",
    "HU-17": "HU 17 - Reactivar recurso",
    "HU-18": "HU 18 - Asignar recursos a un servicio",
    "HU-19": "HU 19 - Definir horarios de atención de un recurso",
    "HU-20": "HU 20 - Consultar disponibilidad de un servicio",
    "HU-21": "HU 21 - Bloquear un horario puntual",
    "HU-22": "HU 22 - Crear reserva",
    "HU-23": "HU 23 - Consultar mis reservas",
    "HU-24": "HU 24 - Consultar reservas del negocio",
    "HU-25": "HU 25 - Cancelar reserva como cliente",
    "HU-26": "HU 26 - Cancelar reserva como proveedor",
    "HU-28": "HU 28 - Cancelar reservas futuras al eliminar un usuario",
    "HU-27": "HU 27 - Completar reserva",
}

# ---------------------------------------------------------------------------
# HU del sprint. tier 1 = compromiso firme (núcleo end-to-end de reserva);
# tier 2 = alcance de la "lista reducida" si el ritmo lo permite; el resto
# (HU-07, 10, 11, 12, 15, 21, 27) queda fuera del compromiso (ver plan).
# pts = puntos del poker (docx de planning). owner = A/B/C (ver ROL_TXT).
# Los CP-xx son IDs PROVISIONALES de casos de prueba; Calidad asigna los definitivos.
# ---------------------------------------------------------------------------
HUS = [
    dict(hu="HU-09", pts=5, tier=1, owner="B", deps="HU-03",
         tablas="services (id, business_id, name, description, duration_minutes, price_cop, active, created_by, created_at, updated_at; UNIQUE(business_id, lower(name)))",
         endpoints=["POST /api/v1/businesses/{businessId}/services", "GET /api/v1/businesses/{businessId}/services"],
         reglas=["Obligatorios: nombre, duración (entero > 0, en minutos) y precio en COP (>= 0; 0 = gratuito)",
                 "El servicio nace ACTIVO; nombre único por negocio sin distinguir mayúsculas (409)",
                 "Solo el dueño del negocio; negocio ajeno 403, Cliente 403, sin sesión 401; el negocio sale del path, nunca del body",
                 "Sin recursos asignados no se puede reservar (mensaje de 'sin disponibilidad')",
                 "Auditar quién y cuándo lo creó"],
         pruebas=["CP-HU09-01 creación válida (201, activo)", "CP-HU09-02 precio 0 (gratuito)",
                  "CP-HU09-03 falta nombre/duración/precio (400 por campo)", "CP-HU09-04 duración 0, -30, abc",
                  "CP-HU09-05 precio -5000, abc", "CP-HU09-06 nombre repetido (409)", "CP-HU09-07 negocio ajeno 403",
                  "CP-HU09-08 Cliente 403", "CP-HU09-09 sin sesión 401", "CP-HU09-10 registro de auditoría"]),
    dict(hu="HU-13", pts=3, tier=1, owner="B", deps="HU-09",
         tablas="(sin tablas nuevas; consultas sobre businesses y services)",
         endpoints=["GET /api/v1/businesses?page=&size=", "GET /api/v1/businesses/{businessId}"],
         reglas=["Solo usuarios autenticados; HU-20 también exige sesión (decidido 2026-10-08)",
                 "Solo negocios con proveedor registrado; cada servicio muestra nombre, descripción, duración y precio",
                 "Solo servicios ACTIVOS; solo lectura; paginación con tope máximo",
                 "Catálogo vacío -> lista vacía + mensaje; negocio inexistente -> 404; sin sesión 401"],
         pruebas=["CP-HU13-01 listar negocios", "CP-HU13-02 detalle con servicios", "CP-HU13-03 catálogo vacío",
                  "CP-HU13-04 negocio sin servicios", "CP-HU13-05 negocio inexistente 404", "CP-HU13-06 sin sesión 401",
                  "CP-HU13-07 servicio inactivo no aparece"]),
    dict(hu="HU-14", pts=5, tier=1, owner="C", deps="HU-03",
         tablas="resources (id, business_id, name, type, active, created_at, updated_at; UNIQUE(business_id, lower(name)))",
         endpoints=["POST /api/v1/businesses/{businessId}/resources", "GET /api/v1/businesses/{businessId}/resources"],
         reglas=["Solo Proveedor autenticado (Cliente 403); nombre y tipo obligatorios",
                 "Tipo: lista fija SALA | EQUIPO | PERSONAL (a confirmar vs. 'consultorio, sala, cancha, puesto' de la descripción)",
                 "Nombre único dentro del negocio (409)",
                 "El recurso queda SIEMPRE en el negocio de la ruta del proveedor autenticado (ajeno 403); un negocio enviado en el body se ignora"],
         pruebas=["CP-HU14-01 registro válido", "CP-HU14-02 sin nombre", "CP-HU14-03 sin tipo", "CP-HU14-04 nombre duplicado 409",
                  "CP-HU14-05 negocio ajeno en body se ignora", "CP-HU14-06 Cliente 403", "CP-HU14-07 sin sesión 401"]),
    dict(hu="HU-18", pts=5, tier=1, owner="B", deps="HU-09, HU-14",
         tablas="service_resources (service_id, resource_id, PK compuesta, FK ON DELETE CASCADE)",
         endpoints=["PUT /api/v1/services/{serviceId}/resources  {resourceIds:[...]}", "GET /api/v1/services/{serviceId}/resources"],
         reglas=["Servicio y recursos deben pertenecer al negocio del proveedor autenticado",
                 "Recurso ajeno o inexistente -> 400 y se rechaza TODA la operación (atómica), sin asignar nada; mismo mensaje en ambos casos",
                 "PUT reemplaza el conjunto (idempotente); permite varios recursos por servicio",
                 "Es requisito de HU-20 (disponibilidad) y HU-22 (reserva)"],
         pruebas=["CP-HU18-01 asignar un recurso", "CP-HU18-02 asignar varios", "CP-HU18-03 recurso de otro negocio",
                  "CP-HU18-04 recurso inexistente", "CP-HU18-05 servicio ajeno 403", "CP-HU18-06 Cliente 403"]),
    dict(hu="HU-19", pts=3, tier=1, owner="C", deps="HU-14",
         tablas="resource_availability (id, resource_id, day_of_week 1-7, start_time, end_time; CHECK start<end; sin traslapes por día)",
         endpoints=["PUT /api/v1/resources/{resourceId}/availability", "PUT /api/v1/resources/{resourceId}/availability/{dayOfWeek}", "GET /api/v1/resources/{resourceId}/availability"],
         reglas=["Rangos HH:mm con inicio < fin (14:00-10:00, 09:00-09:00, 25:00-26:00, ab:cd -> 400)",
                 "Rangos del mismo día no pueden traslaparse; día sin rangos = no disponible",
                 "Editar un día no cambia los demás días ni otros recursos",
                 "Recurso ajeno 403, Cliente 403, sin sesión 401; auditar quién y cuándo modificó",
                 "Zona horaria de negocio: America/Bogota; sin horario definido no se puede reservar"],
         pruebas=["CP-HU19-01 horario completo", "CP-HU19-02 editar solo lunes", "CP-HU19-03 domingo no disponible",
                  "CP-HU19-04 rangos inválidos (4 ejemplos)", "CP-HU19-05 traslape -> 400", "CP-HU19-06 recurso ajeno 403",
                  "CP-HU19-07 Cliente 403", "CP-HU19-08 sin sesión 401", "CP-HU19-09 auditoría"]),
    dict(hu="HU-20", pts=5, tier=1, owner="B", deps="HU-18, HU-19",
         tablas="(sin tablas nuevas; consulta sobre resource_availability, service_resources y bookings)",
         endpoints=["GET /api/v1/services/{serviceId}/availability?date=yyyy-MM-dd   (pública, sin sesión)"],
         reglas=["Fecha ISO yyyy-MM-dd; inválida (32/13/2026, ab/cd/efgh, 2026-99-99) -> 400; pasada -> 400; sin fecha = hoy",
                 "Horarios = disponibilidad de recursos activos asignados - reservas CONFIRMADAS - (ahora + antelación mínima)",
                 "Sin disponibilidad o todo reservado -> mensaje 'no hay horarios'; servicio inexistente/inactivo -> 404 'no disponible'",
                 "Pública, sin sesión (escenario \"usuario sin sesión\" del AC); los cambios de horario del recurso se ven en la consulta siguiente",
                 "Rendimiento: índices por (resource_id, start_at) y paginación/tope de rango de fechas"],
         pruebas=["CP-HU20-01 horarios libres", "CP-HU20-02 excluye reservados", "CP-HU20-03 día sin disponibilidad",
                  "CP-HU20-04 todo reservado", "CP-HU20-05 fecha pasada", "CP-HU20-06 fecha inválida (3 ejemplos)",
                  "CP-HU20-07 servicio inexistente", "CP-HU20-08 sin fecha = hoy", "CP-HU20-09 sin sesión permitido (público)",
                  "CP-HU20-10 dos consultas simultáneas"]),
    dict(hu="HU-22", pts=8, tier=1, owner="B", deps="HU-08, HU-09, HU-14, HU-18, HU-19, HU-20",
         tablas="bookings (id, client_id, client_email, service_id, service_name, business_name, resource_id, start_at, end_at, status, cancel_reason, price_cop, created_at, cancelled_at) + EXCLUDE USING gist (anti-traslape por recurso) [requiere btree_gist]",
         endpoints=["POST /api/v1/bookings  {serviceId, date, startTime, endTime[, resourceId]}"],
         reglas=["Solo rol Cliente; estado inicial CONFIRMADA (automática); la respuesta incluye el id",
                 "El cliente envía fecha, hora de inicio y hora de fin (AC); fin > inicio y duración = la del servicio; fecha obligatoria",
                 "Servicio activo, con recurso activo asignado y dentro del horario del recurso",
                 "Inicio >= ahora + antelación mínima del negocio (1 h por defecto, HU-08)",
                 "Anti-overbooking por RECURSO: restricción EXCLUDE en BD + 409 'horario no disponible' ante concurrencia",
                 "Guarda el precio vigente y datos de historial (snapshots) para sobrevivir a ediciones/eliminaciones"],
         pruebas=["CP-HU22-01 reserva válida (CONFIRMADA + id)", "CP-HU22-02 horario ocupado 409", "CP-HU22-03 sin fecha 400",
                  "CP-HU22-04 fin <= inicio / rango inválido", "CP-HU22-05 sin sesión 401", "CP-HU22-06 proveedor no puede reservar 403",
                  "CP-HU22-07 menos de la antelación mínima", "CP-HU22-08 fuera del horario del recurso",
                  "CP-HU22-09 servicio/recurso inactivo", "CP-HU22-10 concurrencia: 2 reservas simultáneas, 1 gana",
                  "CP-HU22-11 snapshot de precio"]),
    dict(hu="HU-23", pts=5, tier=1, owner="B", deps="HU-22",
         tablas="(sin tablas nuevas)",
         endpoints=["GET /api/v1/bookings/me?status=&page=&size=", "GET /api/v1/users/{userId}/bookings (403 si no es el propio)"],
         reglas=["Solo las reservas del Cliente autenticado; orden por fecha de más reciente a más antigua",
                 "Cada reserva: fecha, hora inicio/fin, servicio, estado y motivo de cancelación si aplica",
                 "Lista vacía -> mensaje 'no tiene reservas'; no existe ruta para ver reservas de otro usuario (403/404)",
                 "Sin sesión 401"],
         pruebas=["CP-HU23-01 listado con reservas", "CP-HU23-02 sin reservas", "CP-HU23-03 intento de ver ajenas",
                  "CP-HU23-04 sin sesión 401", "CP-HU23-05 orden descendente", "CP-HU23-06 muestra motivo de cancelación"]),
    dict(hu="HU-24", pts=5, tier=1, owner="C", deps="HU-22",
         tablas="(sin tablas nuevas)",
         endpoints=["GET /api/v1/businesses/{businessId}/bookings?from=&to=&status=&page=&size="],
         reglas=["Solo el Proveedor dueño del negocio; negocio ajeno 403; Cliente 403; sin sesión 401",
                 "Muestra todas las reservas de sus servicios: fecha, horas, servicio y nombre del Cliente",
                 "Orden descendente por fecha; lista vacía -> mensaje 'no hay reservas'"],
         pruebas=["CP-HU24-01 listado del negocio", "CP-HU24-02 sin reservas", "CP-HU24-03 negocio ajeno 403",
                  "CP-HU24-04 Cliente 403", "CP-HU24-05 sin sesión 401", "CP-HU24-06 filtros de fecha/estado"]),
    dict(hu="HU-25", pts=5, tier=1, owner="B", deps="HU-23",
         tablas="(usa bookings: status, cancel_reason, cancelled_at)",
         endpoints=["POST /api/v1/bookings/{bookingId}/cancellation"],
         reglas=["Solo el Cliente dueño de la reserva CONFIRMADA; con al menos 1 hora de antelación (regla fija de plataforma)",
                 "Estado -> CANCELADA con motivo CLIENTE; el horario queda libre (el EXCLUDE solo cuenta CONFIRMADA)",
                 "Fuera de plazo o ya cancelada -> 409 con mensaje claro; ajena -> 403",
                 "El aviso al proveedor del AC queda como evento de auditoría (notificaciones están fuera de alcance)"],
         pruebas=["CP-HU25-01 cancelación exitosa", "CP-HU25-02 con menos de 1 h", "CP-HU25-03 reserva ajena 403",
                  "CP-HU25-04 ya cancelada 409", "CP-HU25-05 el horario vuelve a estar disponible (HU-20)",
                  "CP-HU25-06 sin sesión 401"]),
    dict(hu="HU-08", pts=3, tier=1, owner="B", deps="HU-03",
         tablas="businesses (+ min_advance_hours INT NOT NULL DEFAULT 1)",
         endpoints=["PUT /api/v1/businesses/{businessId}/booking-lead-time  {hours}", "GET /api/v1/businesses/{businessId}"],
         reglas=["Valor por defecto 1 hora; cada negocio tiene el suyo; unidad: horas enteras >= 1 (a confirmar)",
                 "Solo el dueño del negocio (ajeno 403, Cliente 403, sin sesión 401)",
                 "Valores inválidos (0, -2, abc) -> 400 y se conserva el anterior",
                 "No afecta reservas ya confirmadas; HU-22 lo exige en las nuevas"],
         pruebas=["CP-HU08-01 valor por defecto", "CP-HU08-02 definir 2 h", "CP-HU08-03 inválidos 0/-2/abc",
                  "CP-HU08-04 negocio ajeno 403", "CP-HU08-05 Cliente 403", "CP-HU08-06 sin sesión 401",
                  "CP-HU08-07 reservas previas intactas", "CP-HU08-08 se aplica en HU-22"]),
    dict(hu="HU-16", pts=5, tier=2, owner="C", deps="HU-14, HU-22, HU-24",
         tablas="(usa resources.active y bookings)",
         endpoints=["POST /api/v1/resources/{resourceId}/deactivation  {confirm:boolean}"],
         reglas=["Sin reservas futuras -> desactiva directo",
                 "Con reservas futuras y sin confirm -> 409 CONFIRMATION_REQUIRED con `affectedBookings` y el recurso sigue activo",
                 "Con confirm=true -> desactiva y cancela las futuras con motivo RECURSO_NO_DISPONIBLE (sin regla de 1 h); el historial se conserva",
                 "Una sola transacción; recurso ajeno 403; patrón de confirmación reutilizable en HU-11/21/28"],
         pruebas=["CP-HU16-01 sin reservas futuras", "CP-HU16-02 con futuras pide confirmación (cuenta)",
                  "CP-HU16-03 confirma y cancela", "CP-HU16-04 no confirma, sin cambios", "CP-HU16-05 recurso ajeno 403",
                  "CP-HU16-06 atomicidad ante falla", "CP-HU16-07 el recurso deja de ofrecer horarios"]),
    dict(hu="HU-17", pts=3, tier=2, owner="C", deps="HU-16",
         tablas="(usa resources.active)",
         endpoints=["POST /api/v1/resources/{resourceId}/reactivation"],
         reglas=["Reactiva el MISMO recurso (sin duplicar) y vuelve a recibir reservas",
                 "Las reservas canceladas por la desactivación permanecen canceladas",
                 "Recurso ajeno 403; ya activo -> respuesta idempotente"],
         pruebas=["CP-HU17-01 reactivar", "CP-HU17-02 sin duplicar", "CP-HU17-03 recurso ajeno 403",
                  "CP-HU17-04 reservas canceladas siguen canceladas", "CP-HU17-05 vuelve a aparecer en disponibilidad"]),
    dict(hu="HU-26", pts=5, tier=2, owner="C", deps="HU-24",
         tablas="(usa bookings: cancel_reason, cancel_note)",
         endpoints=["POST /api/v1/bookings/{bookingId}/provider-cancellation  {reason}"],
         reglas=["Solo el Proveedor dueño del negocio de la reserva; motivo (texto) obligatorio",
                 "Estado CANCELADA con motivo PROVEEDOR + texto; sin regla de 1 h (a confirmar)",
                 "El horario queda libre; el Cliente verá el motivo en HU-23; reserva ajena 403",
                 "El aviso al cliente queda como evento de auditoría (notificaciones fuera de alcance)"],
         pruebas=["CP-HU26-01 cancelación con motivo", "CP-HU26-02 sin motivo 400", "CP-HU26-03 reserva de otro negocio 403",
                  "CP-HU26-04 Cliente 403", "CP-HU26-05 el cliente ve el motivo", "CP-HU26-06 horario liberado"]),
    dict(hu="HU-28", pts=5, tier=2, owner="A", deps="HU-05, HU-22, HU-26",
         tablas="(usa bookings; FKs hacia users/services/resources con SET NULL + snapshots)",
         endpoints=["DELETE /api/v1/users/{userId}  (extiende HU-05)"],
         reglas=["En la MISMA transacción: reservas futuras del usuario (como cliente o dueño del negocio) -> CANCELADA con motivo ELIMINACION_CUENTA (sin regla de 1 h)",
                 "Las reservas pasadas se conservan como historial (por eso bookings guarda snapshots y no cae en cascada)",
                 "Servicios/recursos del proveedor eliminado dejan de ser reservables",
                 "Autoeliminación de cuenta (cliente/proveedor): decisión pendiente; hoy solo Administrador (HU-05)",
                 "Notificaciones fuera de alcance -> evento de auditoría"],
         pruebas=["CP-HU28-01 cliente con reservas futuras", "CP-HU28-02 proveedor con reservas futuras",
                  "CP-HU28-03 reservas pasadas se conservan", "CP-HU28-04 admin elimina usuario", "CP-HU28-05 atomicidad",
                  "CP-HU28-06 admin no puede eliminar admin (HU-05)"]),
]

# Horas por plantilla según puntos: [BD, servicio, controlador, seguridad, unit, integración, verificación]
HORAS_POR_PTS = {3: [2, 3, 2, 1, 2, 2, 1], 5: [3, 4, 3, 2, 3, 3, 1], 8: [4, 6, 4, 3, 5, 4, 2]}

# ---------------------------------------------------------------------------
# Historias "técnicas" (habilitadoras) que agrupan las tareas por componente de Azure.
# ---------------------------------------------------------------------------
TECH_STORIES = {
    "TECH-01": dict(titulo="TECH-01 · Seguridad de acceso: MFA y control de intentos", lote="L01", puntos=8,
                    desc="Cierra el MFA que quedó a medias en el Sprint 1 y protege registro, login y MFA contra fuerza bruta (OWASP A07). Detalle: docs/sprint-2/cierre-pendientes-sprint-1.md"),
    "TECH-02": dict(titulo="TECH-02 · Plataforma: CI/CD y calidad", lote="L01", puntos=5,
                    desc="Pipeline en GitHub Actions y análisis estático (SonarCloud) con los Quality Gates del Sprint 2: cobertura >= 65%, deuda <= 2 días, complejidad < 50, severidad Minor+, 0 vulnerabilidades críticas."),
    "TECH-03": dict(titulo="TECH-03 · Arquitectura, API y seguridad del Sprint 2", lote="L01", puntos=8,
                    desc="Documentación interactiva de la API (Swagger), modelo de datos coordinado con BD, diagramas y ADRs, y revisión OWASP Top 10: los entregables de Arquitectura de Software del Sprint 2."),
}

# ---------------------------------------------------------------------------
# TAREAS DE AZURE de las historias técnicas (una por componente). Las horas salen de los
# pendientes internos que cubre (`cubre`, ver PENDIENTES), para no mantener dos números.
# ---------------------------------------------------------------------------
TAREAS_TECNICAS = [
    dict(codigo="SEG-01", story="TECH-01", owner="A", activity="Development", prio=1,
         titulo="Autenticación multifactor (TOTP) obligatoria para administradores: enrolamiento forzoso, login en dos pasos y confirmación en operaciones sensibles",
         desc="Hoy 'obligatorio' solo genera un evento de auditoría. Política en ADR-004; diseño y matriz de pruebas en docs/sprint-2/cierre-pendientes-sprint-1.md §2.",
         cubre=["MFA-01", "MFA-02", "MFA-03", "MFA-04", "MFA-09", "MFA-10", "MFA-11"]),
    dict(codigo="SEG-02", story="TECH-01", owner="A", activity="Development", prio=1,
         titulo="Control de intentos reutilizable para registro, login y MFA (429 por origen)",
         desc="Generaliza el limitador de registro (atómico) y lo aplica al login, al código MFA y a la confirmación de operaciones sensibles. OWASP A07.",
         cubre=["MFA-05"]),
    dict(codigo="PLT-01", story="TECH-02", owner="C", activity="Deployment", prio=1,
         titulo="Pipeline CI/CD con GitHub Actions: build, pruebas, cobertura (JaCoCo), imagen Docker y entorno de pruebas desplegado",
         desc="Disparo en PR a dev/main. El entorno de pruebas (Render) lo usa Calidad del 15 al 20 de octubre. Las pruebas de integración dependen de la base común con Testcontainers (SP1-05).",
         cubre=["CI-01", "CI-03", "CI-07"]),
    dict(codigo="PLT-02", story="TECH-02", owner="C", activity="Deployment", prio=1,
         titulo="Análisis estático de calidad (SonarCloud) con los Quality Gates del Sprint 2",
         desc="Requiere permisos de administrador/owner de la organización de GitHub (Calidad ya los pidió). Incluye depurar los hallazgos iniciales.",
         cubre=["CI-02", "CI-04"]),
    dict(codigo="ARQ-01", story="TECH-03", owner="B", activity="Development", prio=1,
         titulo="Documentación interactiva de la API (Swagger/OpenAPI)",
         desc="Spike de compatibilidad de springdoc con Spring Boot 4.1/Jackson 3 (timebox 2 h), esquema Bearer, ejemplos y catálogo de errores del Sprint 2.",
         cubre=["API-01", "API-02"]),
    dict(codigo="ARQ-02", story="TECH-03", owner="A", activity="Design", prio=1,
         titulo="Modelo de datos del Sprint 2: convenciones y migraciones Flyway coordinadas con BD",
         desc="Convenciones (UUID, TIMESTAMPTZ, ON DELETE) y un único responsable de las migraciones V5+. El MER y las pruebas con volumen son entregables del equipo de BD.",
         cubre=["BD-01", "BD-03"]),
    dict(codigo="ARQ-03", story="TECH-03", owner="C", activity="Design", prio=1,
         titulo="Arquitectura del Sprint 2: diagrama de despliegue, diagrama de componentes y ADRs",
         desc="Entregable explícito de Arquisoft para el Sprint 2. ADRs previstos: MFA (004), anti-overbooking (005) y retención de historial (006); los redacta Juan Esteban.",
         cubre=["DOC-01", "DOC-02", "DOC-03"]),
    dict(codigo="ARQ-04", story="TECH-03", owner="A", activity="Development", prio=1,
         titulo="Seguridad OWASP Top 10: revisión del código y endurecimiento (cabeceras, CORS, límites de entrada)",
         desc="Matriz A01–A10 contra el código en docs/sprint-2/plan-de-trabajo-sprint-2.md §8.",
         cubre=["OWASP-01", "OWASP-02"]),
]

# ---------------------------------------------------------------------------
# PENDIENTES INTERNOS — checklist fino de trabajo. NO se sube a Azure.
# Cada entrada = (código, área, "Task", título, activity, horas, owner, prioridad, descripción).
# Sirve para estimar capacidad (plan §2), para citar pendientes en los documentos y como detalle
# del alcance de las tareas de Azure que lo `cubre`n (TAREAS_TECNICAS).
# ---------------------------------------------------------------------------
PENDIENTES = [
    # --- Bugs reportados por Calidad (GitHub #8–#11): corrección = sin tarea de Azure ---
    ("BUG-8", "Bugs de Calidad", "Task", "[#8] El 6.º intento de registro supera el límite y crea la cuenta (HU-01)", "Development", 6, "A", 1,
     "RegistrationRateLimiter bloquea con attempts > MAX_ATTEMPTS: el 6.º intento ya pasó isBlocked() y se procesa; recién el 7.º se rechaza. isBlocked+registerAttempt no es atómico. Corrección y regresión en cierre-pendientes-sprint-1.md §3."),
    ("BUG-9", "Bugs de Calidad", "Task", "[#9] Sin validación de longitud en nombre, correo y contraseña (HU-01/02/03)", "Development", 5, "A", 2,
     "Los DTO solo validan formato, sin @Size. Valores largos llegan a la BD (VARCHAR 150/255) o a BCrypt (72 bytes)."),
    ("BUG-10", "Bugs de Calidad", "Task", "[#10] Registro concurrente con el mismo correo devuelve 500 en lugar de 409 (HU-01)", "Development", 7, "A", 1,
     "Comprobar-y-luego-insertar no es atómico; la violación de uk_users_email/uk_users_phone sale como DataIntegrityViolationException y cae al manejador genérico."),
    ("BUG-11", "Bugs de Calidad", "Task", "[#11] Cuerpo vacío en registro y login devuelve 500 en lugar de 400 (HU-01/02/03)", "Development", 6, "A", 1,
     "HttpMessageNotReadableException (y 405/415/404/UUID inválido) no tienen manejador y caen al @ExceptionHandler(Exception)."),
    # --- MFA y pendientes de Sprint 1 ---
    ("MFA-01", "MFA", "Task", "ADR-004: política de MFA (quién, enrolamiento obligatorio, step-up, recuperación)", "Documentation", 2, "A", 1,
     "Decidir y documentar la política. Propuesta en docs/sprint-2/cierre-pendientes-sprint-1.md §2. Salida: docs/arquitectura/adr/ADR-004-politica-mfa.md aprobado por los 3 de Arquisoft."),
    ("MFA-02", "MFA", "Task", "Enrolamiento obligatorio de ADMIN: sin MFA activo solo puede usar /auth/mfa/* y logout (403 MFA_ENROLLMENT_REQUIRED)", "Development", 5, "A", 1,
     "Hoy 'obligatorio' solo genera un evento de auditoría (HU-05). Incluye al admin creado por AdminBootstrapRunner. Cambios: JwtAuthenticationFilter/authorities, handler 403 específico, tests."),
    ("MFA-03", "MFA", "Task", "Login en dos pasos: 401 MFA_REQUIRED cuando la contraseña es válida y falta el código", "Development", 2, "A", 1,
     "Código erróneo sigue dando el 401 genérico. Documentar en Swagger. AC HU-02 'Verificación adicional para cuentas administrativas'."),
    ("MFA-04", "MFA", "Task", "Step-up MFA (header X-MFA-Code) en PATCH /users/{id}/role y DELETE /users/{id}", "Development", 4, "A", 1,
     "AC HU-02 'Verificación adicional para operaciones sensibles'. Reutilizable para cambio de contraseña y reset de MFA."),
    ("MFA-05", "MFA", "Task", "Límite de intentos en login y verificación MFA (429, por IP+correo)", "Development", 5, "A", 1,
     "Hoy solo el registro tiene límite. OWASP A07. Generalizar RegistrationRateLimiter en un limitador reutilizable y atómico."),
    ("MFA-06", "MFA", "Task", "Anti-replay TOTP: guardar last_used_step (migración V5) y rechazar códigos ya usados", "Development", 3, "A", 2, "Evita reutilizar un código dentro de la ventana de ±1 paso."),
    ("MFA-07", "MFA", "Task", "Cifrar el secreto MFA en reposo (AES-256-GCM, clave MFA_ENCRYPTION_KEY por entorno)", "Development", 4, "A", 2,
     "OWASP A02. AttributeConverter + migración de filas existentes + variable en Render y .env.example."),
    ("MFA-08", "MFA", "Task", "Reset de MFA por otro administrador (DELETE /users/{id}/mfa) + runbook de recuperación", "Development", 4, "A", 3, "Sin recuperación una cuenta admin queda bloqueada si pierde el autenticador."),
    ("MFA-09", "MFA", "Task", "Pruebas unitarias MFA: vectores RFC 6238, ventana ±1, replay, step-up, estados del enrolamiento", "Testing", 4, "A", 1, "Matriz de casos TC-MFA-xx en cierre-pendientes-sprint-1.md §2.4."),
    ("MFA-10", "MFA", "Task", "Prueba de integración del flujo MFA completo (setup→activate→login con código→step-up) con Testcontainers", "Testing", 4, "A", 1, "Corre en CI (Docker disponible en GitHub Actions)."),
    ("MFA-11", "MFA", "Task", "Prueba manual E2E con app autenticadora real + colección Postman + evidencia", "Testing", 2, "A", 1, "Google/Microsoft Authenticator. Guardar capturas en docs/sprint-2/evidencias/."),
    ("MFA-12", "MFA", "Task", "Guía operativa 'Configurar MFA' (administrador) y guía para QA (calcular el código TOTP)", "Documentation", 2, "A", 2, "Incluye script de cálculo de código para pruebas sin teléfono."),
    ("SP1-01", "Brechas de Sprint 1", "Task", "HU-02: cambiar la propia contraseña con re-autenticación (PUT /users/me/password), revocar otras sesiones, auditar", "Development", 8, "A", 2,
     "Cierra la brecha 17a de docs/verificacion-criterios-aceptacion-sprint-1.md. Pide contraseña actual (+ código MFA si está activo). Incluye pruebas."),
    ("SP1-02", "Brechas de Sprint 1", "Task", "HU-05: mensaje de auto-modificación para cualquier rol (evaluar auto-modificación antes del chequeo de rol)", "Development", 2, "A", 3, "Brecha 32 de la verificación de criterios. Cambio pequeño + test."),
    ("SP1-03", "Brechas de Sprint 1", "Task", "HU-04: definir y probar el comportamiento de doble logout / logout sin sesión (mensaje claro)", "Development", 2, "A", 3, "Brecha 27. Decidir idempotencia o mensaje diferenciado y documentarlo."),
    ("SP1-04", "Brechas de Sprint 1", "Task", "HU-01: documentar la decisión 'el registro no autentica' (nota en endpoints/ADR)", "Documentation", 1, "A", 3, "Brecha 1: el AC es un OR; se cumple la rama 'redirige a login'."),
    ("SP1-05", "Brechas de Sprint 1", "Task", "Base común de pruebas de integración con Testcontainers (incluye ReservasBackendApplicationTests) lista para CI", "Development", 3, "A", 1,
     "Hoy contextLoads depende de un Postgres local y fallaría en CI. Probar también el bump de Testcontainers 1.21.3 con Docker Desktop encendido."),
    ("SP1-06", "Brechas de Sprint 1", "Task", "Limpiar referencias a documentos inexistentes (HU-01-checklist, matriz-actualizaciones) y el Javadoc desactualizado de IdentityServiceImpl", "Development", 1, "A", 4, "Deuda de documentación en comentarios."),
    ("SEC-01", "Higiene de seguridad", "Task", "Rotar la contraseña del admin demo y quitar credenciales de docs públicos (el repo es público)", "Deployment", 1, "A", 1,
     "docs/guia-prueba-aplicacion-desplegada.md contiene admin@example.com + contraseña. Entregar credenciales por canal privado; con MFA obligatorio (MFA-02) el riesgo baja."),
    ("SEC-02", "Higiene de seguridad", "Task", "Confirmar rotación de la contraseña de Supabase expuesta en el historial (commit 5ae30bd)", "Deployment", 1, "A", 1, "Pendiente desde 2026-09-13; confirmar con Santiago."),
    ("SEC-03", "Higiene de seguridad", "Task", "Quitar el 'Using generated security password' (UserDetailsService vacío o excluir la autoconfiguración)", "Development", 1, "A", 3, "OWASP A05. Es ruido de log inofensivo pero evitable."),
    # --- Plataforma: CI/CD y calidad ---
    ("CI-01", "Plataforma (CI/CD y calidad)", "Task", "Workflow GitHub Actions: build + test + JaCoCo + build de imagen Docker (en .github/workflows/ci.yml)", "Deployment", 4, "C", 1, "La ruta correcta lleva punto inicial (.github). Disparar en PR a dev y main."),
    ("CI-02", "Plataforma (CI/CD y calidad)", "Task", "Habilitar SonarCloud/SonarQube con los gates del Sprint 2 (requiere admin/owner de la organización GitHub)", "Deployment", 3, "C", 1,
     "Calidad pidió permisos o ser owner. El repo es público: SonarCloud es gratis. Guardar SONAR_TOKEN como secreto del repo."),
    ("CI-03", "Plataforma (CI/CD y calidad)", "Task", "Ajustar JaCoCo (exclusiones, reporte XML para Sonar) y verificar cobertura >= 65%", "Development", 2, "C", 1, "Línea base 2026-10-07: 77,1% de líneas con 101 pruebas unitarias."),
    ("CI-04", "Plataforma (CI/CD y calidad)", "Task", "Triage de hallazgos iniciales de Sonar: deuda <= 2 días, complejidad < 50, 0 vulnerabilidades críticas", "Development", 4, "C", 1, "Se hace cuando Sonar esté habilitado."),
    ("CI-05", "Plataforma (CI/CD y calidad)", "Task", "Protección de ramas main/dev (PR obligatorio + CI verde + 1 revisión)", "Deployment", 1, "C", 2, "Requiere admin del repo."),
    ("CI-06", "Plataforma (CI/CD y calidad)", "Task", "Dependabot / OWASP Dependency-Check en CI (job semanal o por PR)", "Development", 2, "C", 3, "OWASP A06. Dependency-check 13.x, requiere clave NVD."),
    ("CI-07", "Plataforma (CI/CD y calidad)", "Task", "Entorno de pruebas para Calidad (Render) con datos semilla y credenciales fuera del repo", "Deployment", 2, "C", 1, "Calidad prueba del 15 al 20 de octubre."),
    # --- API, seguridad y documentación ---
    ("API-01", "API, seguridad y documentación", "Task", "Swagger/OpenAPI con springdoc 3.1.x: spike de compatibilidad con Spring Boot 4.1/Jackson 3, esquema Bearer, ejemplos y errores", "Development", 5, "B", 1,
     "Timebox 2 h para el spike. Probar desde Swagger UI con 'Authorize'. Permitir /v3/api-docs y /swagger-ui en SecurityConfig; deshabilitable por variable."),
    ("API-02", "API, seguridad y documentación", "Task", "Documentar contratos y códigos de error del Sprint 2 (errores-api-sprint-2.md + ejemplos en Swagger)", "Documentation", 2, "B", 2, "Incluye CONFIRMATION_REQUIRED, MFA_REQUIRED, MFA_ENROLLMENT_REQUIRED."),
    ("OWASP-01", "API, seguridad y documentación", "Task", "Revisión OWASP Top 10 (A01–A10): matriz contra el código, hallazgos y correcciones", "Development", 6, "A", 1, "Plantilla en docs/sprint-2/plan-de-trabajo-sprint-2.md §8."),
    ("OWASP-02", "API, seguridad y documentación", "Task", "Cabeceras de seguridad, CORS explícito, límites de tamaño de payload y de paginación", "Development", 3, "A", 2, "OWASP A05/A04."),
    ("DOC-01", "API, seguridad y documentación", "Task", "Diagrama de despliegue (cliente, backend Spring Boot, PostgreSQL, Render, GitHub Actions, Sonar)", "Design", 3, "C", 1, "Entregable explícito del plan de Arquisoft."),
    ("DOC-02", "API, seguridad y documentación", "Task", "Actualizar arquitectura: módulos service/resource/reservation y ADR-004 (MFA), ADR-005 (anti-overbooking), ADR-006 (retención de historial)", "Documentation", 5, "B", 1, "Base de la nota de 'Arquitectura de Solución' (25%)."),
    ("DOC-03", "API, seguridad y documentación", "Task", "Diagramas de componentes y paquetes del Sprint 2 con tabla de conexiones", "Design", 4, "C", 2, "Convención del repo: docs/arquitectura/diagramas/."),
    ("DOC-04", "API, seguridad y documentación", "Task", "Evidencias del sprint: reportes Sonar/JaCoCo, pipeline verde, Postman, capturas", "Documentation", 3, "B", 2, "Carpeta docs/sprint-2/evidencias/."),
    ("DOC-05", "API, seguridad y documentación", "Task", "Actualizar resultados de pruebas y estado del proyecto (Sprint 2)", "Documentation", 2, "B", 2, "docs/resultados-pruebas-sprint-2.md y docs/estado-proyecto-sprint-2.md."),
    # --- Modelo de datos del Sprint 2 ---
    ("BD-01", "Modelo de datos", "Task", "Acordar convenciones (UUID, TIMESTAMPTZ, ON DELETE, nombres) y alinear el modelo formal con el esquema Flyway real", "Design", 3, "A", 1,
     "BLOQUEANTE de BD-02. Ver docs/conciliacion-modelo-bd-sprint-1.md: UUID, CASCADE/SET NULL (no RESTRICT hacia users), rol ADMINISTRADOR."),
    ("BD-02", "Modelo de datos", "Task", "MER y entidades Sprint 2: services, resources, service_resources, resource_availability, bookings (+ columnas de businesses)", "Design", 4, "BD", 1, "Día 1–2 del plan."),
    ("BD-03", "Modelo de datos", "Task", "Modelo físico y DDL propuesto → migraciones Flyway V5+ revisadas por Arquisoft", "Development", 4, "A", 1, "El backend commitea las migraciones; BD revisa. Un solo origen de verdad. Simon = puente backend↔BD."),
    ("BD-04", "Modelo de datos", "Task", "Restricción anti-overbooking (EXCLUDE USING gist, btree_gist) + índices de disponibilidad + prueba de concurrencia", "Development", 4, "A", 1, "Comprobar btree_gist en Render Postgres 16."),
    ("BD-05", "Modelo de datos", "Task", "Consultas clave del Sprint 2 (disponibilidad, reservas por cliente/negocio, cancelaciones masivas) con EXPLAIN", "Design", 4, "B", 2, "docs/bd/modelo/consultas-clave-sprint-2.md. Juan Esteban = consultas de BD."),
    ("BD-06", "Modelo de datos", "Task", "Scripts finales y pruebas con volumen (datos sintéticos)", "Testing", 4, "BD", 2, "Día 6–7 del plan de BD."),
    ("BD-07", "Modelo de datos", "Task", "Seguridad en BD: rol de aplicación con privilegios mínimos, sin DROP/TRUNCATE, secretos fuera del repo", "Development", 2, "C", 3, "OWASP A05. Santiago = seguridad y pruebas de BD."),
    # --- Estudio y sustentación (no va a Azure) ---
    ("EST-A1", "Estudio y sustentación", "Task", "Estudio dirigido (Dev A): identidad, JWT/sesiones, MFA, OWASP — lectura + traza de petición + hoja resumen", "Requirements", 8, "A", 1, "Ver plan-estudio-y-sustentacion.md, pista 1."),
    ("EST-B1", "Estudio y sustentación", "Task", "Estudio dirigido (Dev B): catálogo, disponibilidad y reservas — reglas de negocio, flujo y anti-overbooking", "Requirements", 8, "B", 1, "Ver plan-estudio-y-sustentacion.md, pista 2."),
    ("EST-C1", "Estudio y sustentación", "Task", "Estudio dirigido (Dev C): arquitectura, BD, CI/CD, despliegue y calidad", "Requirements", 8, "C", 1, "Ver plan-estudio-y-sustentacion.md, pista 3."),
    ("EST-X1", "Estudio y sustentación", "Task", "Revisión cruzada de PR: cada integrante revisa PR de una pista distinta (continuo durante el sprint)", "Development", 6, "A", 2, "Estudio aplicado: nadie aprueba su propia pista."),
    ("EST-X2", "Estudio y sustentación", "Task", "Ensayo de sustentación n.º 1 (con banco de preguntas)", "Requirements", 3, "A", 1, "Día 6–7."),
    ("EST-X3", "Estudio y sustentación", "Task", "Ensayo de sustentación n.º 2 + demo en vivo contra el entorno desplegado", "Requirements", 3, "A", 1, "Día 8, antes de entregar a Calidad."),
    ("EST-X4", "Estudio y sustentación", "Task", "Retrospectiva del Sprint 2 con evidencia (acta/captura) — exigida en Gestión (Lineamientos §3.7)", "Requirements", 1, "A", 2, "Después de la review del 20 de octubre."),
    # --- Derivadas de los Lineamientos (LIN) y de la Agenda ---
    ("AZ-01", "API, seguridad y documentación", "Task", "Organizar Azure: importar los lotes, asignar responsables y la iteración 'Sprint 2', vincular HU a Features/Épicas", "Requirements", 2, "A", 1,
     "Día 1 del plan ('Organizar Azure y Tasks'). Cada tarea lleva responsable y estimación (LIN §3.6). Ver azure-boards/README.md."),
    ("GIT-01", "Plataforma (CI/CD y calidad)", "Task", "Acordar y documentar el flujo de ramas (trunk-based, feature/<HU>-<desc>, PR + revisión + CI) y el destino de la rama dev", "Requirements", 1, "C", 1,
     "LIN §7.1 pide trunk-based con rama principal protegida; el repo hoy tiene main + dev. Santiago tiene asignada la 'estrategia de ramas' (Sprint 1 ArquisuaveBD)."),
    ("API-03", "API, seguridad y documentación", "Task", "Errores y logs según Lineamientos §3.3: traceId (correlación) en ApiError y logs estructurados en JSON — decidir alcance", "Development", 3, "A", 3,
     "El formato de error vigente ({timestamp,status,error,message,path,fields}) reemplazó al de los Lineamientos (errorCode/details/traceId). Confirmar con el docente/Calidad si se exige para S2."),
    ("PERF-01", "Plataforma (CI/CD y calidad)", "Task", "Prueba de carga básica del RNF base (200 solicitudes/min, respuesta <= 30 s) con k6 o Gatling sobre login, catálogo y disponibilidad", "Testing", 3, "C", 3,
     "Evidencia no funcional; en Render free hay cold start de ~2 min (calentar el servicio antes de medir)."),
    ("DOC-06", "API, seguridad y documentación", "Task", "Actualizar la Matriz de HU y la trazabilidad HU→Regla→API→Componente→Tabla→Prueba del Sprint 2", "Documentation", 2, "B", 2,
     "La matriz y el código no deben divergir (Prompt maestro). Base generada: azure-boards/trazabilidad-sprint-2.md."),
    # --- Descubiertos al implementar (2026-10-07) ---
    ("SP1-07", "Brechas de Sprint 1", "Task", "Los rechazos auditados (REJECTED) se perdían al revertirse la transacción: noRollbackFor en login, registro, aprovisionamiento y cambio de rol", "Development", 2, "A", 1,
     "Los servicios @Transactional auditan el rechazo y lanzan la excepción; el rollback se llevaba el evento. Probado con AuditPersistenceIntegrationTest (fallaba en 3 de 4 antes de la corrección)."),
    ("SP1-08", "Brechas de Sprint 1", "Task", "Las pruebas de integración de Sprint 1 no corrían (Jackson 3, Testcontainers vs Docker 29) y el límite de registro las rompía", "Development", 3, "A", 1,
     "Imports de Jackson 3 en las IT; Testcontainers 1.21.3 -> 1.21.4; límite de registro configurable (security.rate-limit.*) y relajado en el perfil de pruebas; aserción de expiresIn tolerante."),
    ("OWASP-03", "API, seguridad y documentación", "Task", "IP real del cliente detrás del proxy de Render (server.forward-headers-strategy=native) para los límites de intentos y la auditoría; verificar en el despliegue", "Development", 1, "A", 2,
     "Hoy getRemoteAddr() puede ser la IP del proxy: los límites por IP se vuelven casi globales y la auditoría guarda la IP del proxy. No se pudo verificar sin el entorno de Render."),
    ("SEC-04", "Higiene de seguridad", "Task", "Pedir la contraseña también en POST /auth/mfa/setup: con un token robado de un administrador sin MFA se podría enrolar el autenticador del atacante", "Development", 3, "A", 3,
     "Riesgo documentado en ADR-004 §4. Mitigación inmediata: enrolar al administrador del bootstrap justo después de desplegar."),
]

# Avance de los pendientes internos (código -> (estado, nota)); estado: "hecho" | "parcial".
# Actualizar al terminar cada bloque de trabajo; el generador lo muestra y sugiere el estado de las
# tareas de Azure que los agrupan (todos hechos -> Closed tras revisión; algunos -> Active).
AVANCE = {
    "BUG-8": ("hecho", "2026-10-07 · AttemptLimiter atómico (el 6.º intento se rechaza). Regresión: AttemptLimiterTest, RateLimitersTest, RegistrationRateLimitIntegrationTest"),
    "BUG-9": ("hecho", "2026-10-07 · @Size en los DTO y tope de 72 bytes. Regresión: RequestLengthValidationTest, ApiErrorHandlingIntegrationTest"),
    "BUG-10": ("hecho", "2026-10-07 · DataIntegrityViolationException -> 409/400. Regresión: RegistrationConcurrencyIntegrationTest (sin el manejador devolvía [500, 201])"),
    "BUG-11": ("hecho", "2026-10-07 · manejadores 400/404/405/415/406. Regresión: GlobalExceptionHandlerTest, ApiErrorHandlingIntegrationTest (sin ellos, 12 de 19 daban 500)"),
    "MFA-01": ("hecho", "2026-10-07 · ADR-004 redactado; estado Propuesto: falta la aprobación de los tres de Arquisoft"),
    "MFA-02": ("hecho", "2026-10-07 · MfaEnrollmentFilter (403 MFA_ENROLLMENT_REQUIRED)"),
    "MFA-03": ("hecho", "2026-10-07 · login en dos pasos (401 MFA_REQUIRED)"),
    "MFA-04": ("hecho", "2026-10-07 · StepUpService + header X-MFA-Code en PATCH role y DELETE user"),
    "MFA-05": ("hecho", "2026-10-07 · AuthAttemptLimiter para login y confirmación (429 tras 5 fallos)"),
    "MFA-09": ("hecho", "2026-10-07 · TotpServiceTest con vectores RFC 6238, AuthServiceImplTest, StepUpServiceImplTest, MfaEnrollmentFilterTest"),
    "MFA-10": ("hecho", "2026-10-07 · MfaFlowIntegrationTest contra PostgreSQL real (14 casos)"),
    "MFA-11": ("parcial", "2026-10-07 · verificación E2E local 28/28 contra la app real con el script de TOTP; falta app autenticadora real, colección Postman y evidencia en docs/sprint-2/evidencias/"),
    "MFA-12": ("parcial", "2026-10-07 · guía de prueba desplegada actualizada con el flujo de MFA; falta la guía operativa de recuperación"),
    "SP1-05": ("hecho", "2026-10-07 · AbstractIntegrationTest (contenedor único); ReservasBackendApplicationTests ya no depende de localhost:5432"),
    "SP1-07": ("hecho", "2026-10-07 · noRollbackFor; AuditPersistenceIntegrationTest"),
    "SP1-08": ("hecho", "2026-10-07 · 258 pruebas en verde con Docker"),
    "BD-01": ("parcial", "2026-10-08 · docs/bd/convenciones-bd.md redactado como propuesta; falta acordarlo con BD (Andraus) y alinear el modelo formal"),
}

# ---------------------------------------------------------------------------
# CAMBIOS: componentes NUEVOS que no cubre ninguna tarea de arriba. Criterio:
#  - ¿Corrección, bug, refactor, ajuste, prueba, documentación suelta o higiene?  -> SIN tarea de Azure
#    (se resuelve en el PR con `Fixes #n`; si hay detalle que recordar, va en PENDIENTES).
#  - ¿Ya lo cubre una tarea existente (HU09-API, SEG-01...)?  -> no se agrega nada: se mueve esa tarea
#    a Active / Closed en Azure y se avisa en el handoff.
#  - ¿Componente o parte nueva de la aplicación, vista general/arquitectónica?  -> UNA llamada chg(...),
#    en un LOTE NUEVO.
# Parámetros de chg():
#   codigo   CHG-nnn estable (úsalo en el commit/PR: "[CHG-006] ...")
#   padre    de qué cuelga: una HU ("HU-09") o una historia técnica ("TECH-01"...). Si ya existe en Azure,
#            su ID va en STORY_IDS / TECH_IDS (así no se crea otra historia).
#   estado   "New" (por hacer) | "Closed" (ya realizado; horas = trabajo completado)
#   lote     lote CSV (uno NUEVO por tanda: L02, L03, ...; nunca reusar uno ya subido)
# ---------------------------------------------------------------------------
CAMBIOS = []


def chg(codigo, titulo, activity, horas, padre, desc="", estado="New", lote="L02", owner="A"):
    CAMBIOS.append(dict(codigo=codigo, titulo=titulo, activity=activity, horas=horas, desc=desc,
                        estado=estado, lote=lote, padre=padre, owner=owner))


# (Vacío: el backlog base ya cubre todo lo planificado.)

# IDs de Azure de las historias técnicas ya creadas por un import (anotar después de importar L01;
# los lotes siguientes cuelgan de ese ID en vez de crear otra historia).
TECH_IDS = {"TECH-01": 114, "TECH-02": 117, "TECH-03": 120}

# Estado de subida de cada lote a Azure (editar cuando el usuario confirme que lo importó).
# Un lote que no esté aquí se considera "pendiente".
# NOTA 2026-10-07: la primera versión (L01-L07, 173 tareas) se DESCARTÓ por demasiado detallada; nunca se
# marcó como subida. L01 es ahora la línea base simplificada.
ESTADO_LOTES = {"L01": "subido"}
NOMBRE_LOTES = {"L01": "tareas-por-componente"}
