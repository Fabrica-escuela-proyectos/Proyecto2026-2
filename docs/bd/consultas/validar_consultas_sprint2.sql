-- ============================================================================
-- Validación SQL Sprint 2 (parametrizado)
-- ============================================================================
-- Ejecutar después de cargar fixtures:
--   \i docs/bd/consultas/datos_ejemplo_sprint2.sql
--   \i docs/bd/consultas/validar_consultas_sprint2.sql
--
-- Objetivo: validar que el SQL parametrizado de consultas_sprint2.sql
-- compila y se ejecuta sobre el esquema real (UUID + bookings).
-- ============================================================================

-- C1
PREPARE q1(uuid) AS
SELECT s.id
FROM services s
JOIN service_resources sr ON sr.service_id = s.id
JOIN resources r ON r.id = sr.resource_id
WHERE s.business_id = $1
  AND s.active = TRUE
  AND r.active = TRUE
ORDER BY s.name;
EXECUTE q1('30000000-0000-0000-0000-000000000001'::uuid);
DEALLOCATE q1;

-- C2
PREPARE q2(uuid, uuid, date) AS
WITH params AS (
    SELECT r.id AS resource_id,
           s.duration_minutes * INTERVAL '1 minute' AS duration,
           $3 AS requested_day
    FROM resources r
    JOIN service_resources sr ON sr.resource_id = r.id
    JOIN services s ON s.id = sr.service_id
    WHERE r.id = $1
      AND s.id = $2
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
SELECT sl.start_at, sl.end_at
FROM slots sl
WHERE sl.start_at > now()
  AND NOT EXISTS (
      SELECT 1 FROM bookings b
      WHERE b.resource_id = sl.resource_id
        AND b.status = 'CONFIRMADA'
        AND tstzrange(b.start_at, b.end_at) && tstzrange(sl.start_at, sl.end_at)
  )
ORDER BY sl.start_at;
EXECUTE q2(
    '50000000-0000-0000-0000-000000000001'::uuid,
    '40000000-0000-0000-0000-000000000001'::uuid,
    current_date + 7
);
DEALLOCATE q2;

BEGIN;

-- C3
PREPARE q3(uuid, text, text, uuid, text, uuid, text, uuid, text, timestamptz, timestamptz, bigint) AS
INSERT INTO bookings (
    client_id, client_email, client_name,
    business_id, business_name,
    service_id, service_name,
    resource_id, resource_name,
    start_at, end_at,
    status, price_cop
)
SELECT $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, 'CONFIRMADA', $12
WHERE NOT EXISTS (
    SELECT 1 FROM bookings b
    WHERE b.resource_id = $8
      AND b.status = 'CONFIRMADA'
      AND b.start_at < $11
      AND b.end_at > $10
)
RETURNING id;
EXECUTE q3(
    '10000000-0000-0000-0000-000000000001'::uuid,
    'cliente.demo@example.com',
    'Cliente Demo',
    '30000000-0000-0000-0000-000000000001'::uuid,
    'Centro Deportivo Demo',
    '40000000-0000-0000-0000-000000000001'::uuid,
    'Alquiler de cancha',
    '50000000-0000-0000-0000-000000000001'::uuid,
    'Cancha 1',
    ((current_date + 7) + TIME '11:00') AT TIME ZONE 'America/Bogota',
    ((current_date + 7) + TIME '12:00') AT TIME ZONE 'America/Bogota',
    40000
);
DEALLOCATE q3;

-- C6
PREPARE q6(uuid, uuid, text) AS
UPDATE bookings
SET status = 'CANCELADA',
    cancel_origin = 'CLIENTE',
    cancel_reason = $3,
    cancelled_at = now()
WHERE id = $1
  AND client_id = $2
  AND status = 'CONFIRMADA'
  AND start_at > now() + INTERVAL '1 hour'
RETURNING id;
EXECUTE q6(
    '60000000-0000-0000-0000-000000000001'::uuid,
    '10000000-0000-0000-0000-000000000001'::uuid,
    'Validación automatizada'
);
DEALLOCATE q6;

-- C8
PREPARE q8(uuid) AS
SELECT NOT EXISTS (
    SELECT 1 FROM bookings b
    WHERE b.service_id = $1
      AND b.status = 'CONFIRMADA'
      AND b.start_at > now()
) AS can_disable_service;
EXECUTE q8('40000000-0000-0000-0000-000000000001'::uuid);
DEALLOCATE q8;

ROLLBACK;
