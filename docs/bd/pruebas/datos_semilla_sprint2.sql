-- ============================================================
-- Datos de ejemplo Sprint 2 sobre el esquema REAL (Flyway V1–V12). Re-ejecutable.
-- Uso: BD local o de pruebas, para correr docs/bd/consultas/consultas_sprint2.sql (UUID fijos).
-- NO es para Render: allí los usuarios se crean por la API (colección de Postman) porque la
-- contraseña debe quedar con el hash BCrypt de la aplicación. password_hash aquí es un marcador:
-- con estos usuarios no se puede iniciar sesión.
-- Responsable BD: Juan Sebastián Andraus López.
-- ============================================================

INSERT INTO users (id, full_name, email, phone_number, password_hash) VALUES
 ('10000000-0000-0000-0000-000000000001', 'Cliente Demo',   'cliente.demo@example.com',   '3000000001', 'NO_ES_UN_HASH'),
 ('10000000-0000-0000-0000-000000000002', 'Proveedor Demo', 'proveedor.demo@example.com', '3000000002', 'NO_ES_UN_HASH')
ON CONFLICT (id) DO NOTHING;

-- Roles del catálogo de V1 (CLIENTE, PROVEEDOR, ADMINISTRADOR), por nombre
INSERT INTO user_roles (user_id, role_id)
SELECT v.u::uuid, r.id
FROM (VALUES ('10000000-0000-0000-0000-000000000001', 'CLIENTE'),
             ('10000000-0000-0000-0000-000000000002', 'PROVEEDOR')) v(u, rol)
JOIN roles r ON r.name = v.rol
ON CONFLICT DO NOTHING;

INSERT INTO providers (id, user_id) VALUES
 ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002')
ON CONFLICT (id) DO NOTHING;

INSERT INTO businesses (id, provider_id, name, min_advance_hours) VALUES
 ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'Centro Deportivo Demo', 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO services (id, business_id, name, description, duration_minutes, price_cop, created_by) VALUES
 ('40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 'Alquiler de sala',  'Sala de ensayo', 60,  80000, '10000000-0000-0000-0000-000000000002'),
 ('40000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', 'Clase personalizada', 'Con instructor', 90, 120000, '10000000-0000-0000-0000-000000000002')
ON CONFLICT (id) DO NOTHING;

INSERT INTO resources (id, business_id, name, type, created_by) VALUES
 ('50000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 'Sala 1',      'SALA',     '10000000-0000-0000-0000-000000000002'),
 ('50000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', 'Sala 2',      'SALA',     '10000000-0000-0000-0000-000000000002'),
 ('50000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000001', 'Instructora', 'PERSONAL', '10000000-0000-0000-0000-000000000002')
ON CONFLICT (id) DO NOTHING;

INSERT INTO service_resources (service_id, resource_id) VALUES
 ('40000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000001'),
 ('40000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000002'),
 ('40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000003')
ON CONFLICT DO NOTHING;

-- Lunes a sábado 08:00–18:00; domingo 09:00–13:00 (hora local de Bogotá)
INSERT INTO resource_availability (resource_id, day_of_week, start_time, end_time)
SELECT r.id::uuid, d,
       CASE WHEN d = 7 THEN TIME '09:00' ELSE TIME '08:00' END,
       CASE WHEN d = 7 THEN TIME '13:00' ELSE TIME '18:00' END
FROM (VALUES ('50000000-0000-0000-0000-000000000001'), ('50000000-0000-0000-0000-000000000002'),
             ('50000000-0000-0000-0000-000000000003')) r(id),
     generate_series(1, 7) d
WHERE NOT EXISTS (SELECT 1 FROM resource_availability ra WHERE ra.resource_id = r.id::uuid);

-- Reservas: una futura (dentro de 3 días, 10:00 Bogotá), una pasada completada y una cancelada
INSERT INTO bookings (id, client_id, client_email, client_name, business_id, business_name,
                      service_id, service_name, resource_id, resource_name, start_at, end_at,
                      status, price_cop, cancel_origin, cancel_reason, cancelled_at)
SELECT v.id::uuid, '10000000-0000-0000-0000-000000000001', 'cliente.demo@example.com', 'Cliente Demo',
       '30000000-0000-0000-0000-000000000001', 'Centro Deportivo Demo',
       '40000000-0000-0000-0000-000000000001', 'Alquiler de sala',
       '50000000-0000-0000-0000-000000000001', 'Sala 1',
       ((((now() AT TIME ZONE 'America/Bogota')::date + v.dias) + v.hora)                     AT TIME ZONE 'America/Bogota'),
       ((((now() AT TIME ZONE 'America/Bogota')::date + v.dias) + v.hora + INTERVAL '1 hour') AT TIME ZONE 'America/Bogota'),
       v.estado, 80000, v.origen, v.motivo, CASE WHEN v.estado = 'CANCELADA' THEN now() END
FROM (VALUES
  ('60000000-0000-0000-0000-000000000001',  3, TIME '10:00', 'CONFIRMADA', NULL,      NULL),
  ('60000000-0000-0000-0000-000000000002', -7, TIME '10:00', 'COMPLETADA', NULL,      NULL),
  ('60000000-0000-0000-0000-000000000003',  4, TIME '15:00', 'CANCELADA',  'CLIENTE', 'Cambio de planes')
) v(id, dias, hora, estado, origen, motivo)
WHERE NOT EXISTS (SELECT 1 FROM bookings b WHERE b.id = v.id::uuid);

SELECT 'datos de ejemplo cargados' AS estado,
       (SELECT count(*) FROM services  WHERE business_id = '30000000-0000-0000-0000-000000000001') AS servicios,
       (SELECT count(*) FROM resources WHERE business_id = '30000000-0000-0000-0000-000000000001') AS recursos,
       (SELECT count(*) FROM bookings  WHERE client_id   = '10000000-0000-0000-0000-000000000001') AS reservas;
