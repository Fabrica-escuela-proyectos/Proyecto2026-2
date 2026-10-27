-- ============================================================
-- Consultas clave Sprint 2 (BD-05) sobre el esquema REAL (Flyway V1–V12).
-- Son la versión SQL de lo que ejecuta el backend (repositorios JPA de reservation/, resource/, service/),
-- para revisar planes (EXPLAIN) y justificar índices. Responsable BD: Juan Sebastián Andraus López.
-- Ejecutables tal cual tras docs/bd/pruebas/datos_semilla_sprint2.sql (UUID fijos en el CTE p).
-- Las de escritura (C09–C12) van dentro de BEGIN … ROLLBACK para no alterar los datos de ejemplo.
-- Planes y tiempos con 200.000 reservas: docs/bd/modelo/documentacion_bd_sprint2.md §4.
-- ============================================================

-- C01 · HU-13 · Catálogo: servicios activos de un negocio con sus recursos activos
--   Índices: idx_services_business_id, service_resources PK, resources PK
WITH p AS (SELECT '30000000-0000-0000-0000-000000000001'::uuid AS business_id)
SELECT s.id, s.name, s.duration_minutes, s.price_cop,
       count(r.id) FILTER (WHERE r.active) AS recursos_activos
FROM p
JOIN services s                ON s.business_id = p.business_id AND s.active
LEFT JOIN service_resources sr ON sr.service_id = s.id
LEFT JOIN resources r          ON r.id = sr.resource_id
GROUP BY s.id
ORDER BY s.name;

-- C02 · HU-19 / HU-20 · Horario de un recurso en un día (ResourceAvailabilityRepository)
--   Índice: idx_resource_availability_resource_day
WITH p AS (SELECT '50000000-0000-0000-0000-000000000001'::uuid AS resource_id, 2 AS dia)
SELECT ra.start_time, ra.end_time
FROM p JOIN resource_availability ra ON ra.resource_id = p.resource_id AND ra.day_of_week = p.dia
ORDER BY ra.start_time;

-- C03 · HU-20 · Disponibilidad pública de un servicio en una fecha local (cálculo completo en SQL)
--   El backend arma las franjas en Java con C02 + C04; esta versión SQL sirve para revisar el resultado
--   y el costo. Respeta recursos activos, horario del día, antelación mínima (HU-08) y reservas CONFIRMADAS.
WITH p AS (
    SELECT '40000000-0000-0000-0000-000000000001'::uuid AS service_id,
           ((now() AT TIME ZONE 'America/Bogota')::date + 3) AS fecha
), svc AS (
    SELECT s.id, make_interval(mins => s.duration_minutes) AS dur, b.min_advance_hours
    FROM p JOIN services s ON s.id = p.service_id AND s.active
    JOIN businesses b ON b.id = s.business_id
), franjas AS (
    SELECT r.id AS resource_id, r.name AS resource_name,
           (g AT TIME ZONE 'America/Bogota')             AS start_at,
           ((g + svc.dur) AT TIME ZONE 'America/Bogota') AS end_at,
           svc.min_advance_hours
    FROM p CROSS JOIN svc
    JOIN service_resources sr     ON sr.service_id = svc.id
    JOIN resources r              ON r.id = sr.resource_id AND r.active
    JOIN resource_availability ra ON ra.resource_id = r.id AND ra.day_of_week = EXTRACT(ISODOW FROM p.fecha)
    CROSS JOIN LATERAL generate_series(p.fecha + ra.start_time, p.fecha + ra.end_time - svc.dur, svc.dur) g
)
SELECT f.start_at, f.end_at, f.resource_id, f.resource_name
FROM franjas f
WHERE f.start_at >= now() + make_interval(hours => f.min_advance_hours)
  AND NOT EXISTS (SELECT 1 FROM bookings b              -- misma expresión que el EXCLUDE: usa su índice GiST
                  WHERE b.resource_id = f.resource_id AND b.resource_id IS NOT NULL AND b.status = 'CONFIRMADA'
                    AND tstzrange(b.start_at, b.end_at) && tstzrange(f.start_at, f.end_at))
ORDER BY f.start_at, f.resource_name;

-- C04 · HU-20 · Reservas confirmadas que tocan un día local para varios recursos (findConfirmedTouching)
--   Índice: idx_bookings_resource_start (o ex_bookings_no_overlap)
WITH p AS (
    SELECT ARRAY['50000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000002']::uuid[] AS recursos,
           (((now() AT TIME ZONE 'America/Bogota')::date + 3)::timestamp AT TIME ZONE 'America/Bogota') AS dia_ini
)
SELECT b.resource_id, b.start_at, b.end_at
FROM p JOIN bookings b ON b.resource_id = ANY (p.recursos)
WHERE b.status = 'CONFIRMADA' AND b.start_at < p.dia_ini + INTERVAL '1 day' AND b.end_at > p.dia_ini;

-- C05 · HU-22 · ¿El recurso está libre? (existsConfirmedOverlapping; antes del INSERT, con el recurso bloqueado)
--   Índice: ex_bookings_no_overlap / idx_bookings_resource_start. La carrera la cubre el EXCLUDE (23P01 → 409).
WITH p AS (
    SELECT '50000000-0000-0000-0000-000000000001'::uuid AS resource_id,
           ((((now() AT TIME ZONE 'America/Bogota')::date + 3) + TIME '10:30') AT TIME ZONE 'America/Bogota') AS ini
)
SELECT EXISTS (SELECT 1 FROM p JOIN bookings b ON b.resource_id = p.resource_id
               WHERE b.status = 'CONFIRMADA' AND b.start_at < p.ini + INTERVAL '1 hour' AND b.end_at > p.ini) AS ocupado;

-- C06 · HU-22 · Asignación automática: primer recurso activo y libre del servicio, por nombre (ADR-005 D6)
WITH p AS (
    SELECT '40000000-0000-0000-0000-000000000001'::uuid AS service_id,
           ((((now() AT TIME ZONE 'America/Bogota')::date + 3) + TIME '10:00') AT TIME ZONE 'America/Bogota') AS ini
)
SELECT r.id, r.name
FROM p
JOIN services s           ON s.id = p.service_id AND s.active
JOIN service_resources sr ON sr.service_id = s.id
JOIN resources r          ON r.id = sr.resource_id AND r.active
WHERE NOT EXISTS (SELECT 1 FROM bookings b
                  WHERE b.resource_id = r.id AND b.status = 'CONFIRMADA'
                    AND b.start_at < p.ini + make_interval(mins => s.duration_minutes) AND b.end_at > p.ini)
ORDER BY r.name
LIMIT 1;

-- C07 · HU-23 · Mis reservas (findByClientId paginado). Usa las copias: no une con services/resources.
--   Índice: idx_bookings_client_id (client_id, start_at)
WITH p AS (SELECT '10000000-0000-0000-0000-000000000001'::uuid AS client_id)
SELECT b.id, b.status, b.business_name, b.service_name, b.resource_name, b.start_at, b.end_at,
       b.price_cop, b.cancel_origin, b.cancel_reason
FROM p JOIN bookings b ON b.client_id = p.client_id
ORDER BY b.start_at DESC, b.id
LIMIT 20 OFFSET 0;

-- C08 · HU-24 · Reservas de un negocio con filtros (from/to en día local de Bogotá, status), paginado
--   Índice: idx_bookings_business_id (business_id, start_at)
WITH p AS (
    SELECT '30000000-0000-0000-0000-000000000001'::uuid AS business_id,
           ((now() AT TIME ZONE 'America/Bogota')::date - 30) AS desde,
           ((now() AT TIME ZONE 'America/Bogota')::date + 30) AS hasta,
           NULL::varchar AS estado
)
SELECT b.id, b.status, b.client_name, b.client_email, b.service_name, b.resource_name, b.start_at, b.end_at
FROM p JOIN bookings b ON b.business_id = p.business_id
WHERE b.start_at >= (p.desde::timestamp AT TIME ZONE 'America/Bogota')
  AND b.start_at <  ((p.hasta + 1)::timestamp AT TIME ZONE 'America/Bogota')
  AND (p.estado IS NULL OR b.status = p.estado)
ORDER BY b.start_at DESC, b.id
LIMIT 20 OFFSET 0;

-- C09 · HU-25 / HU-26 · Cancelación individual (con la reserva bloqueada: findByIdForUpdate)
BEGIN;
SELECT id, status, start_at FROM bookings WHERE id = '60000000-0000-0000-0000-000000000001' FOR UPDATE;
UPDATE bookings
SET status = 'CANCELADA', cancel_origin = 'CLIENTE', cancel_reason = 'Ejemplo', cancelled_at = now()
WHERE id = '60000000-0000-0000-0000-000000000001' AND status = 'CONFIRMADA'
RETURNING id, status, cancel_origin;
ROLLBACK;

-- C10 · HU-16 · Desactivar recurso: contar (para la confirmación), cancelar futuras confirmadas y desactivar
--   Índice: idx_bookings_resource_start. Todo en una transacción con el recurso bloqueado (ADR-005 D5).
BEGIN;
SELECT active FROM resources WHERE id = '50000000-0000-0000-0000-000000000001' FOR UPDATE;
SELECT count(*) AS afectadas FROM bookings
WHERE resource_id = '50000000-0000-0000-0000-000000000001' AND status = 'CONFIRMADA' AND start_at > now();
UPDATE bookings
SET status = 'CANCELADA', cancel_origin = 'RECURSO_NO_DISPONIBLE', cancel_reason = 'Recurso desactivado', cancelled_at = now()
WHERE resource_id = '50000000-0000-0000-0000-000000000001' AND status = 'CONFIRMADA' AND start_at > now();
UPDATE resources SET active = FALSE, updated_at = now() WHERE id = '50000000-0000-0000-0000-000000000001';
ROLLBACK;

-- C11 · HU-28 · Eliminar cuenta: cancelar futuras del cliente y de sus negocios, luego borrar el usuario
--   Índices: idx_bookings_client_id, idx_bookings_business_id. El DELETE propaga:
--   CASCADE → user_roles, sessions, mfa, providers → businesses → services, resources → service_resources, resource_availability
--   SET NULL → bookings (client_id, business_id, service_id, resource_id), services/resources.created_by
BEGIN;
UPDATE bookings
SET status = 'CANCELADA', cancel_origin = 'ELIMINACION_CUENTA', cancelled_at = now()
WHERE status = 'CONFIRMADA' AND start_at > now()
  AND (client_id = '10000000-0000-0000-0000-000000000002'
       OR business_id IN (SELECT b.id FROM businesses b JOIN providers pr ON pr.id = b.provider_id
                          WHERE pr.user_id = '10000000-0000-0000-0000-000000000002'));
DELETE FROM users WHERE id = '10000000-0000-0000-0000-000000000002';
SELECT count(*) AS reservas_conservadas, count(*) FILTER (WHERE business_id IS NULL) AS sin_negocio
FROM bookings WHERE business_name = 'Centro Deportivo Demo';
ROLLBACK;

-- C12 · HU-18 · Reemplazar los recursos de un servicio (con el servicio bloqueado). Las reservas no dependen
--   de service_resources, así que el historial no bloquea el cambio.
BEGIN;
SELECT id FROM services WHERE id = '40000000-0000-0000-0000-000000000001' FOR UPDATE;
DELETE FROM service_resources WHERE service_id = '40000000-0000-0000-0000-000000000001'
  AND resource_id <> ALL (ARRAY['50000000-0000-0000-0000-000000000002']::uuid[]);
INSERT INTO service_resources (service_id, resource_id)
VALUES ('40000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000002') ON CONFLICT DO NOTHING;
ROLLBACK;

-- C13 · Reporte · Tasa de cancelación por negocio y origen en el mes actual (no es ruta crítica)
SELECT b.business_name, count(*) AS reservas,
       count(*) FILTER (WHERE b.status = 'CANCELADA') AS canceladas,
       round(100.0 * count(*) FILTER (WHERE b.status = 'CANCELADA') / count(*), 1) AS pct_cancelacion,
       count(*) FILTER (WHERE b.cancel_origin = 'CLIENTE')   AS por_cliente,
       count(*) FILTER (WHERE b.cancel_origin = 'PROVEEDOR') AS por_proveedor
FROM bookings b
WHERE b.start_at >= date_trunc('month', now()) AND b.start_at < date_trunc('month', now()) + INTERVAL '1 month'
GROUP BY b.business_name
ORDER BY pct_cancelacion DESC;
