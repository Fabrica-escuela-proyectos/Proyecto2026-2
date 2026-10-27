-- ============================================================
-- Prueba de volumen Sprint 2 (BD-06): 200.000 reservas sobre el esquema REAL (Flyway V1–V12).
-- psql en una BD LOCAL de pruebas (no Render, no SQL Editor):  psql -d <bd> -f pruebas_volumen_sprint2.sql
-- Todo dentro de una transacción con ROLLBACK: no deja datos. Ids UUID deterministas: md5('<prefijo><n>')::uuid.
-- Las consultas son las de docs/bd/consultas/consultas_sprint2.sql (mismo número Cnn).
-- Responsable BD: Juan Sebastián Andraus López.
-- ============================================================
\timing on
BEGIN;

-- 20.000 clientes + 100 proveedores, cada uno con un negocio
INSERT INTO users (id, full_name, email, phone_number, password_hash)
SELECT md5('u'||g)::uuid, 'vol_user_'||g, 'vol'||g||'@vol.test', '7'||lpad(g::text, 9, '0'), 'x'
FROM generate_series(1, 20100) g;
INSERT INTO providers (id, user_id) SELECT md5('p'||g)::uuid, md5('u'||(20000+g))::uuid FROM generate_series(1, 100) g;
INSERT INTO businesses (id, provider_id, name) SELECT md5('b'||g)::uuid, md5('p'||g)::uuid, 'vol_negocio_'||g FROM generate_series(1, 100) g;

-- 5 servicios y 5 recursos por negocio; el servicio n usa los recursos n y n+1
INSERT INTO services (id, business_id, name, duration_minutes, price_cop)
SELECT md5('s'||b||'_'||n)::uuid, md5('b'||b)::uuid, 'vol_serv_'||n, 60, 30000
FROM generate_series(1, 100) b, generate_series(1, 5) n;
INSERT INTO resources (id, business_id, name, type)
SELECT md5('r'||b||'_'||n)::uuid, md5('b'||b)::uuid, 'vol_rec_'||n, 'SALA'
FROM generate_series(1, 100) b, generate_series(1, 5) n;
INSERT INTO service_resources (service_id, resource_id)
SELECT md5('s'||b||'_'||n)::uuid, md5('r'||b||'_'||n)::uuid FROM generate_series(1, 100) b, generate_series(1, 5) n
UNION ALL
SELECT md5('s'||b||'_'||n)::uuid, md5('r'||b||'_'||((n % 5) + 1))::uuid FROM generate_series(1, 100) b, generate_series(1, 5) n;
INSERT INTO resource_availability (resource_id, day_of_week, start_time, end_time)
SELECT md5('r'||b||'_'||n)::uuid, d, '08:00', '18:00'
FROM generate_series(1, 100) b, generate_series(1, 5) n, generate_series(1, 7) d;

-- 200.000 reservas: 500 recursos × 200 días (−100 … +99) × 2 franjas (09:00 y 14:00 Bogotá); 20 % canceladas
INSERT INTO bookings (client_id, client_email, client_name, business_id, business_name, service_id, service_name,
                      resource_id, resource_name, start_at, end_at, status, price_cop, cancel_origin, cancelled_at)
SELECT md5('u'||(1 + ((b*7 + n*3 + (d + 100)*13 + h) % 20000)))::uuid, 'vol@vol.test', 'vol',
       md5('b'||b)::uuid, 'vol_negocio_'||b, md5('s'||b||'_'||n)::uuid, 'vol_serv_'||n,
       md5('r'||b||'_'||n)::uuid, 'vol_rec_'||n,
       ((current_date + d) + make_interval(hours => h))     AT TIME ZONE 'America/Bogota',
       ((current_date + d) + make_interval(hours => h + 1)) AT TIME ZONE 'America/Bogota',
       CASE WHEN (b + n + d + h) % 5 = 0 THEN 'CANCELADA'
            WHEN d < 0 THEN 'COMPLETADA' ELSE 'CONFIRMADA' END,
       30000,
       CASE WHEN (b + n + d + h) % 5 = 0 THEN 'CLIENTE' END,
       CASE WHEN (b + n + d + h) % 5 = 0 THEN now() END
FROM generate_series(1, 100) b, generate_series(1, 5) n, generate_series(-100, 99) d, (VALUES (9), (14)) hh(h);

ANALYZE;
SELECT count(*) AS reservas, count(*) FILTER (WHERE status = 'CANCELADA') AS canceladas,
       count(*) FILTER (WHERE status = 'CONFIRMADA') AS confirmadas FROM bookings;

\echo '--- C03 HU-20 disponibilidad de un servicio en una fecha (+30 días)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, SUMMARY ON)
WITH p AS (SELECT md5('s1_1')::uuid AS service_id, ((now() AT TIME ZONE 'America/Bogota')::date + 30) AS fecha),
svc AS (SELECT s.id, make_interval(mins => s.duration_minutes) AS dur, b.min_advance_hours
        FROM p JOIN services s ON s.id = p.service_id AND s.active JOIN businesses b ON b.id = s.business_id),
franjas AS (
    SELECT r.id AS resource_id, (g AT TIME ZONE 'America/Bogota') AS start_at,
           ((g + svc.dur) AT TIME ZONE 'America/Bogota') AS end_at, svc.min_advance_hours
    FROM p CROSS JOIN svc
    JOIN service_resources sr ON sr.service_id = svc.id
    JOIN resources r ON r.id = sr.resource_id AND r.active
    JOIN resource_availability ra ON ra.resource_id = r.id AND ra.day_of_week = EXTRACT(ISODOW FROM p.fecha)
    CROSS JOIN LATERAL generate_series(p.fecha + ra.start_time, p.fecha + ra.end_time - svc.dur, svc.dur) g)
SELECT f.start_at, f.end_at, f.resource_id FROM franjas f
WHERE f.start_at >= now() + make_interval(hours => f.min_advance_hours)
  AND NOT EXISTS (SELECT 1 FROM bookings b              -- misma expresión que el EXCLUDE: usa su índice GiST
                  WHERE b.resource_id = f.resource_id AND b.resource_id IS NOT NULL AND b.status = 'CONFIRMADA'
                    AND tstzrange(b.start_at, b.end_at) && tstzrange(f.start_at, f.end_at))
ORDER BY f.start_at;

\echo '--- C04 HU-20 reservas confirmadas de los recursos de un servicio en un día'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, SUMMARY ON)
SELECT b.resource_id, b.start_at, b.end_at FROM bookings b
WHERE b.resource_id = ANY (ARRAY[md5('r1_1')::uuid, md5('r1_2')::uuid]) AND b.status = 'CONFIRMADA'
  AND b.start_at < ((current_date + 31)::timestamp AT TIME ZONE 'America/Bogota')
  AND b.end_at   > ((current_date + 30)::timestamp AT TIME ZONE 'America/Bogota');

\echo '--- C05 HU-22 ¿recurso ocupado? (antes de insertar)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, SUMMARY ON)
SELECT EXISTS (SELECT 1 FROM bookings b WHERE b.resource_id = md5('r1_1')::uuid AND b.status = 'CONFIRMADA'
               AND b.start_at < ((current_date + 30) + TIME '10:00') AT TIME ZONE 'America/Bogota'
               AND b.end_at   > ((current_date + 30) + TIME '09:00') AT TIME ZONE 'America/Bogota');

\echo '--- C07 HU-23 mis reservas (página 1)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, SUMMARY ON)
SELECT b.id, b.status, b.business_name, b.service_name, b.resource_name, b.start_at
FROM bookings b WHERE b.client_id = md5('u500')::uuid
ORDER BY b.start_at DESC, b.id LIMIT 20;

\echo '--- C08 HU-24 reservas de un negocio en 60 días (página 1)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, SUMMARY ON)
SELECT b.id, b.status, b.client_name, b.service_name, b.start_at
FROM bookings b
WHERE b.business_id = md5('b1')::uuid
  AND b.start_at >= ((current_date - 30)::timestamp AT TIME ZONE 'America/Bogota')
  AND b.start_at <  ((current_date + 30)::timestamp AT TIME ZONE 'America/Bogota')
ORDER BY b.start_at DESC, b.id LIMIT 20;

\echo '--- C10 HU-16 contar y cancelar futuras de un recurso'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, SUMMARY ON)
SELECT count(*) FROM bookings WHERE resource_id = md5('r1_1')::uuid AND status = 'CONFIRMADA' AND start_at > now();
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, SUMMARY ON)
UPDATE bookings SET status = 'CANCELADA', cancel_origin = 'RECURSO_NO_DISPONIBLE', cancelled_at = now()
WHERE resource_id = md5('r1_1')::uuid AND status = 'CONFIRMADA' AND start_at > now();

\echo '--- C11 HU-28 cancelar futuras de los negocios de un proveedor'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, SUMMARY ON)
UPDATE bookings SET status = 'CANCELADA', cancel_origin = 'ELIMINACION_CUENTA', cancelled_at = now()
WHERE business_id IN (md5('b2')::uuid) AND status = 'CONFIRMADA' AND start_at > now();

\echo '--- C11b HU-28 eliminar un proveedor con 2.000 reservas en su negocio (cascada + SET NULL)'
EXPLAIN (ANALYZE, COSTS OFF, SUMMARY ON)
DELETE FROM users WHERE id = md5('u20003')::uuid;

\echo '--- C13 reporte mensual de cancelación'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, SUMMARY ON)
SELECT business_name, count(*), count(*) FILTER (WHERE status = 'CANCELADA')
FROM bookings
WHERE start_at >= date_trunc('month', now()) AND start_at < date_trunc('month', now()) + INTERVAL '1 month'
GROUP BY business_name;

\echo '--- ADR-005 doble reserva con 200.000 filas (debe fallar con ex_bookings_no_overlap)'
SAVEPOINT s1;
INSERT INTO bookings (client_id, client_email, client_name, business_id, business_name, service_id, service_name,
                      resource_id, resource_name, start_at, end_at, price_cop)
SELECT md5('u1')::uuid, 'x', 'x', business_id, business_name, service_id, service_name, resource_id, resource_name,
       start_at + INTERVAL '30 minutes', end_at + INTERVAL '30 minutes', price_cop
FROM bookings WHERE resource_id = md5('r1_2')::uuid AND status = 'CONFIRMADA' AND start_at > now() LIMIT 1;
ROLLBACK TO s1;

ROLLBACK;
