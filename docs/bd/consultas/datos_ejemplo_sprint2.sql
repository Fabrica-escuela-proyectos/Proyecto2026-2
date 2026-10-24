-- ============================================================================
-- Datos de ejemplo Sprint 2 (FIXTURES DETERMINÍSTICOS)
-- ============================================================================
-- Este archivo sí usa UUIDs fijos para demo/validación manual.
-- No representa SQL de runtime de la aplicación.
--
-- Convención de fixtures:
--   1000... usuarios
--   2000... proveedores
--   3000... negocios
--   4000... servicios
--   5000... recursos
--   6000... reservas
-- ============================================================================

INSERT INTO users (id, full_name, email, phone_number, password_hash, enabled) VALUES
  ('10000000-0000-0000-0000-000000000001'::uuid, 'Cliente Demo',   'cliente.demo@example.com',   '3000000001', 'hash-demo', TRUE),
  ('10000000-0000-0000-0000-000000000002'::uuid, 'Proveedor Demo', 'proveedor.demo@example.com', '3000000002', 'hash-demo', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO providers (id, user_id) VALUES
  ('20000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000002'::uuid)
ON CONFLICT (id) DO NOTHING;

INSERT INTO businesses (id, provider_id, name) VALUES
  ('30000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000001'::uuid, 'Centro Deportivo Demo')
ON CONFLICT (id) DO NOTHING;

INSERT INTO services (id, business_id, name, description, duration_minutes, price_cop, active) VALUES
  ('40000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000001'::uuid,
   'Alquiler de cancha', 'Servicio de demostración', 60, 40000, TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO resources (id, business_id, name, type, active) VALUES
  ('50000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000001'::uuid,
   'Cancha 1', 'SALA', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO service_resources (service_id, resource_id) VALUES
  ('40000000-0000-0000-0000-000000000001'::uuid, '50000000-0000-0000-0000-000000000001'::uuid)
ON CONFLICT (service_id, resource_id) DO NOTHING;

INSERT INTO resource_availability (resource_id, day_of_week, start_time, end_time)
SELECT '50000000-0000-0000-0000-000000000001'::uuid, d, '08:00'::time, '18:00'::time
FROM generate_series(1, 7) AS d
WHERE NOT EXISTS (
    SELECT 1
    FROM resource_availability a
    WHERE a.resource_id = '50000000-0000-0000-0000-000000000001'::uuid
      AND a.day_of_week = d
);

INSERT INTO bookings (
    id,
    client_id, client_email, client_name,
    business_id, business_name,
    service_id, service_name,
    resource_id, resource_name,
    start_at, end_at,
    status, price_cop,
    cancel_reason, cancelled_at, cancel_origin
) VALUES (
    '60000000-0000-0000-0000-000000000001'::uuid,
    '10000000-0000-0000-0000-000000000001'::uuid, 'cliente.demo@example.com', 'Cliente Demo',
    '30000000-0000-0000-0000-000000000001'::uuid, 'Centro Deportivo Demo',
    '40000000-0000-0000-0000-000000000001'::uuid, 'Alquiler de cancha',
    '50000000-0000-0000-0000-000000000001'::uuid, 'Cancha 1',
    ((current_date + 7) + TIME '09:00') AT TIME ZONE 'America/Bogota',
    ((current_date + 7) + TIME '10:00') AT TIME ZONE 'America/Bogota',
    'CONFIRMADA', 40000,
    NULL, NULL, NULL
)
ON CONFLICT (id) DO NOTHING;
