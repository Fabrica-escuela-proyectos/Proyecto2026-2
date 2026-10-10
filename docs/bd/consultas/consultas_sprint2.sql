-- ============================================================
-- Consultas críticas Sprint 2 — PostgreSQL / Supabase
-- Requiere datos: correr antes datos_ejemplo_sprint2.sql (ids de ejemplo: cliente 1, proveedor 2, negocio 1, servicio 1, recurso 1)
-- Se pueden correr todas seguidas sin error (el editor muestra solo el resultado de la última);
-- para ver cada resultado, correr una por una.
-- En el backend los literales van como parámetros (:param). Zona horaria de negocio: America/Bogota
-- ============================================================

-- C1. Servicios activos de un negocio con sus recursos
SELECT s.id, s.name, s.duration_minutes, s.price,
       string_agg(r.name, ', ' ORDER BY r.name) AS recursos
FROM services s
JOIN service_resources sr ON sr.service_id = s.id
JOIN resources r          ON r.id = sr.resource_id AND r.deleted_at IS NULL AND r.is_active
WHERE s.business_id = 1 AND s.deleted_at IS NULL AND s.is_active
GROUP BY s.id
ORDER BY s.name;

-- C2. Disponibilidad: franjas libres de un recurso para un servicio, dentro de 7 días
--     (horario semanal − reservas activas − bloqueos, solo franjas futuras)
WITH params AS (
    SELECT r.id AS resource_id,
           s.duration_minutes * INTERVAL '1 minute' AS dur,
           (current_date + 7) AS d
    FROM resources r
    JOIN service_resources sr ON sr.resource_id = r.id
    JOIN services s           ON s.id = sr.service_id
    WHERE r.id = 1 AND s.id = 1
      AND r.is_active AND r.deleted_at IS NULL
      AND s.is_active AND s.deleted_at IS NULL
), ventanas AS (
    SELECT p.resource_id, p.dur,
           (p.d + rs.start_time) AT TIME ZONE 'America/Bogota' AS v_ini,
           (p.d + rs.end_time)   AT TIME ZONE 'America/Bogota' AS v_fin
    FROM params p
    JOIN resource_schedules rs ON rs.resource_id = p.resource_id
                              AND rs.day_of_week = EXTRACT(ISODOW FROM p.d)
), franjas AS (
    SELECT v.resource_id, g AS inicio, g + v.dur AS fin
    FROM ventanas v,
         LATERAL generate_series(v.v_ini, v.v_fin - v.dur, v.dur) AS g
)
SELECT f.inicio, f.fin
FROM franjas f
WHERE f.inicio > now()
  AND NOT EXISTS (SELECT 1 FROM reservations x
                  WHERE x.resource_id = f.resource_id AND x.status <> 'CANCELADA'
                    AND tstzrange(x.starts_at, x.ends_at) && tstzrange(f.inicio, f.fin))
  AND NOT EXISTS (SELECT 1 FROM resource_blocks b
                  WHERE b.resource_id = f.resource_id
                    AND tstzrange(b.starts_at, b.ends_at) && tstzrange(f.inicio, f.fin))
ORDER BY f.inicio;

-- C3. Crear reserva (dentro de 7 días, 09:00–10:00 hora Bogotá).
--     El EXCLUDE y los triggers rechazan traslapes, pasado, fuera de horario y bloqueos.
--     Si ya existe esa reserva, no inserta nada (ON CONFLICT DO NOTHING) y no devuelve fila.
INSERT INTO reservations (client_id, service_id, resource_id, starts_at, ends_at)
VALUES (1, 1, 1,
        ((current_date + 7) + TIME '09:00') AT TIME ZONE 'America/Bogota',
        ((current_date + 7) + TIME '10:00') AT TIME ZONE 'America/Bogota')
ON CONFLICT DO NOTHING
RETURNING id, status;

-- C4. Reservas de un cliente (próximas primero)
SELECT rv.id, s.name AS servicio, r.name AS recurso, b.name AS negocio,
       rv.starts_at, rv.ends_at, rv.status
FROM reservations rv
JOIN services s   ON s.id = rv.service_id
JOIN resources r  ON r.id = rv.resource_id
JOIN businesses b ON b.id = s.business_id
WHERE rv.client_id = 1
ORDER BY (rv.starts_at < now()), rv.starts_at DESC;

-- C5. Reservas de un negocio en los próximos 30 días (solo el proveedor dueño: HU06)
SELECT rv.id, u.full_name AS cliente, s.name AS servicio, r.name AS recurso,
       rv.starts_at, rv.status
FROM providers p
JOIN businesses b ON b.provider_id = p.id
JOIN services s   ON s.business_id = b.id
JOIN reservations rv ON rv.service_id = s.id
JOIN resources r  ON r.id = rv.resource_id
JOIN users u      ON u.id = rv.client_id
WHERE p.user_id = 2                       -- usuario autenticado (proveedor)
  AND b.id = 1
  AND rv.starts_at >= now()
  AND rv.starts_at <  now() + INTERVAL '30 days'
ORDER BY rv.starts_at;

-- C6. Cancelar la próxima reserva confirmada del cliente 1 (solo si es futura y suya).
--     En el backend se cancela por id: WHERE id = :id AND client_id = :cliente
UPDATE reservations
SET status = 'CANCELADA', cancelled_at = now(), cancelled_by = 1,
    cancellation_reason = 'Cambio de planes'
WHERE id = (SELECT id FROM reservations
            WHERE client_id = 1 AND status = 'CONFIRMADA' AND starts_at > now()
            ORDER BY starts_at LIMIT 1)
RETURNING id, status;

-- C7. Tasa de cancelación por negocio, próximos/últimos 30 días (agregación + ranking)
SELECT b.id AS negocio_id, b.name,
       count(*)                                         AS total,
       count(*) FILTER (WHERE rv.status = 'CANCELADA')  AS canceladas,
       round(100.0 * count(*) FILTER (WHERE rv.status = 'CANCELADA') / count(*), 1) AS pct_cancelacion,
       rank() OVER (ORDER BY count(*) FILTER (WHERE rv.status = 'CANCELADA') DESC) AS ranking
FROM reservations rv
JOIN services s   ON s.id = rv.service_id
JOIN businesses b ON b.id = s.business_id
WHERE rv.starts_at >= now() - INTERVAL '30 days'
  AND rv.starts_at <  now() + INTERVAL '30 days'
GROUP BY b.id, b.name
ORDER BY ranking;

-- C8. ¿Se puede eliminar el servicio 1? (consulta de solo lectura; false si tiene reservas futuras confirmadas)
SELECT NOT EXISTS (SELECT 1 FROM reservations
                   WHERE service_id = 1 AND status = 'CONFIRMADA' AND starts_at > now()) AS se_puede_eliminar;

-- C9. Eliminación lógica del servicio 1 (NO ejecutar en la demo: el trigger la rechaza con
--     SERVICIO_CON_RESERVAS_FUTURAS si hay reservas futuras confirmadas; es la regla funcionando).
-- UPDATE services SET is_active = FALSE, deleted_at = now() WHERE id = 1 AND business_id = 1;
