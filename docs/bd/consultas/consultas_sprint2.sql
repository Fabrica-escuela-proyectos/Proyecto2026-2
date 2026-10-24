-- ============================================================================
-- Consultas Sprint 2 (APLICACIÓN): PostgreSQL parametrizado para backend
-- ============================================================================
-- Este archivo documenta SQL de runtime (no fixtures):
--  - No usa UUIDs hardcodeados.
--  - Usa parámetros nombrados (:businessId, :serviceId, :resourceId, :clientId, :bookingId, :userId, etc.).
--  - Los UUID deben enlazarse tipados (UUID en JPA/JDBC) o con CAST(... AS uuid).
--
-- Para datos determinísticos de demo/validación use:
--   docs/bd/consultas/datos_ejemplo_sprint2.sql
--
-- Zona horaria de negocio: America/Bogota.
-- Tabla de reservas vigente: bookings (no reservations).
-- ============================================================================

-- C1. Servicios activos de un negocio con sus recursos activos
SELECT s.id,
       s.name,
       s.duration_minutes,
       s.price_cop,
       string_agg(r.name, ', ' ORDER BY r.name) AS resources
FROM services s
JOIN service_resources sr ON sr.service_id = s.id
JOIN resources r ON r.id = sr.resource_id
WHERE s.business_id = CAST(:businessId AS uuid)
  AND s.active = TRUE
  AND r.active = TRUE
GROUP BY s.id, s.name, s.duration_minutes, s.price_cop
ORDER BY s.name;

-- C2. Disponibilidad de un recurso para una fecha (franjas por duración del servicio)
-- Parámetros:
--   :resourceId uuid, :serviceId uuid, :date date
WITH params AS (
    SELECT r.id AS resource_id,
           s.duration_minutes * INTERVAL '1 minute' AS duration,
           CAST(:date AS date) AS requested_day
    FROM resources r
    JOIN service_resources sr ON sr.resource_id = r.id
    JOIN services s ON s.id = sr.service_id
    WHERE r.id = CAST(:resourceId AS uuid)
      AND s.id = CAST(:serviceId AS uuid)
      AND r.active = TRUE
      AND s.active = TRUE
), windows AS (
    SELECT p.resource_id,
           p.duration,
           (p.requested_day + a.start_time) AT TIME ZONE 'America/Bogota' AS window_start,
           (p.requested_day + a.end_time)   AT TIME ZONE 'America/Bogota' AS window_end
    FROM params p
    JOIN resource_availability a ON a.resource_id = p.resource_id
                               AND a.day_of_week = EXTRACT(ISODOW FROM p.requested_day)
), slots AS (
    SELECT w.resource_id,
           g AS start_at,
           g + w.duration AS end_at
    FROM windows w,
         LATERAL generate_series(w.window_start, w.window_end - w.duration, w.duration) AS g
)
SELECT sl.start_at,
       sl.end_at
FROM slots sl
WHERE sl.start_at > now()
  AND NOT EXISTS (
      SELECT 1
      FROM bookings b
      WHERE b.resource_id = sl.resource_id
        AND b.status = 'CONFIRMADA'
        AND tstzrange(b.start_at, b.end_at) && tstzrange(sl.start_at, sl.end_at)
  )
ORDER BY sl.start_at;

-- C3. Crear reserva (SQL directo) con snapshots y validación anti-traslape
-- Nota: el backend real bloquea fila de recurso (FOR UPDATE) y valida reglas de negocio antes del INSERT.
INSERT INTO bookings (
    client_id, client_email, client_name,
    business_id, business_name,
    service_id, service_name,
    resource_id, resource_name,
    start_at, end_at,
    status, price_cop
)
SELECT CAST(:clientId AS uuid),
       :clientEmail,
       :clientName,
       CAST(:businessId AS uuid),
       :businessName,
       CAST(:serviceId AS uuid),
       :serviceName,
       CAST(:resourceId AS uuid),
       :resourceName,
       CAST(:startAt AS timestamptz),
       CAST(:endAt AS timestamptz),
       'CONFIRMADA',
       CAST(:priceCop AS bigint)
WHERE NOT EXISTS (
    SELECT 1
    FROM bookings b
    WHERE b.resource_id = CAST(:resourceId AS uuid)
      AND b.status = 'CONFIRMADA'
      AND b.start_at < CAST(:endAt AS timestamptz)
      AND b.end_at > CAST(:startAt AS timestamptz)
)
RETURNING id, status;

-- C4. Reservas del cliente (HU-23): paginadas, más recientes primero
SELECT b.id,
       b.status,
       b.service_id,
       b.service_name,
       b.business_id,
       b.business_name,
       b.resource_id,
       b.resource_name,
       b.start_at,
       b.end_at,
       b.price_cop,
       b.cancel_reason,
       b.cancelled_at,
       b.cancel_origin,
       b.created_at
FROM bookings b
WHERE b.client_id = CAST(:clientId AS uuid)
  AND (:status IS NULL OR b.status = CAST(:status AS varchar(20)))
ORDER BY b.start_at DESC, b.id DESC
LIMIT CAST(:size AS int)
OFFSET CAST(:offset AS int);

-- C5. Reservas de un negocio (HU-24): filtros por estado/fecha local y paginación
-- from/to son fechas de Bogotá, inclusivas por día: [from 00:00, to+1 00:00)
SELECT b.id,
       b.status,
       b.client_id,
       b.client_name,
       b.client_email,
       b.service_name,
       b.resource_name,
       b.start_at,
       b.end_at,
       b.cancel_reason,
       b.cancelled_at,
       b.cancel_origin
FROM bookings b
WHERE b.business_id = CAST(:businessId AS uuid)
  AND (:status IS NULL OR b.status = CAST(:status AS varchar(20)))
  AND (
      :fromDate IS NULL
      OR b.start_at >= (CAST(:fromDate AS date)::timestamp AT TIME ZONE 'America/Bogota')
  )
  AND (
      :toDate IS NULL
      OR b.start_at < ((CAST(:toDate AS date) + 1)::timestamp AT TIME ZONE 'America/Bogota')
  )
ORDER BY b.start_at DESC, b.id DESC
LIMIT CAST(:size AS int)
OFFSET CAST(:offset AS int);

-- C6. Cancelación por cliente (HU-25): solo su reserva CONFIRMADA y futura (+1h)
UPDATE bookings
SET status = 'CANCELADA',
    cancel_origin = 'CLIENTE',
    cancel_reason = :reason,
    cancelled_at = now()
WHERE id = CAST(:bookingId AS uuid)
  AND client_id = CAST(:clientId AS uuid)
  AND status = 'CONFIRMADA'
  AND start_at > now() + INTERVAL '1 hour'
RETURNING id, status, cancel_origin, cancel_reason, cancelled_at;

-- C7. Cancelación por proveedor (HU-26): reserva de su negocio, motivo obligatorio
UPDATE bookings
SET status = 'CANCELADA',
    cancel_origin = 'PROVEEDOR',
    cancel_reason = :reason,
    cancelled_at = now()
WHERE id = CAST(:bookingId AS uuid)
  AND business_id = CAST(:businessId AS uuid)
  AND status = 'CONFIRMADA'
  AND start_at > now()
RETURNING id, status, cancel_origin, cancel_reason, cancelled_at;

-- C8. Regla para desactivar/eliminar servicio: no debe tener reservas futuras confirmadas
SELECT NOT EXISTS (
    SELECT 1
    FROM bookings b
    WHERE b.service_id = CAST(:serviceId AS uuid)
      AND b.status = 'CONFIRMADA'
      AND b.start_at > now()
) AS can_disable_service;

-- C9. Desactivación lógica del servicio (si pasa C8)
UPDATE services
SET active = FALSE,
    updated_at = now()
WHERE id = CAST(:serviceId AS uuid)
  AND business_id = CAST(:businessId AS uuid)
  AND active = TRUE
RETURNING id, active, updated_at;
