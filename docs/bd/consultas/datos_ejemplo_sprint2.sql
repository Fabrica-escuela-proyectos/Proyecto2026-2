-- Datos de ejemplo para probar consultas_sprint2.sql (ids = los usados en las consultas)
-- Ejecutar en una BD de pruebas. Re-ejecutable.
INSERT INTO users (id, full_name, email, phone, password_hash) VALUES
  (1, 'Cliente Demo',    'cliente.demo@example.com',   '3000000001', 'hash-demo'),
  (2, 'Proveedor Demo',  'proveedor.demo@example.com', '3000000002', 'hash-demo')
ON CONFLICT DO NOTHING;
INSERT INTO providers (id, user_id) VALUES (1, 2) ON CONFLICT DO NOTHING;
INSERT INTO businesses (id, provider_id, name) VALUES (1, 1, 'Centro Deportivo Demo') ON CONFLICT DO NOTHING;
INSERT INTO services (id, business_id, name, duration_minutes, price) VALUES (1, 1, 'Alquiler de cancha', 60, 40000) ON CONFLICT DO NOTHING;
INSERT INTO resources (id, business_id, name) VALUES (1, 1, 'Cancha 1') ON CONFLICT DO NOTHING;
INSERT INTO service_resources (service_id, resource_id, business_id) VALUES (1, 1, 1) ON CONFLICT DO NOTHING;
INSERT INTO resource_schedules (resource_id, day_of_week, start_time, end_time)
SELECT 1, d, '08:00', '18:00' FROM generate_series(1,7) d
WHERE NOT EXISTS (SELECT 1 FROM resource_schedules WHERE resource_id = 1);

-- Alinear secuencias para que los próximos INSERT sin id no choquen
SELECT setval(pg_get_serial_sequence('users','id'),      GREATEST((SELECT max(id) FROM users), 1));
SELECT setval(pg_get_serial_sequence('providers','id'),  GREATEST((SELECT max(id) FROM providers), 1));
SELECT setval(pg_get_serial_sequence('businesses','id'), GREATEST((SELECT max(id) FROM businesses), 1));
SELECT setval(pg_get_serial_sequence('services','id'),   GREATEST((SELECT max(id) FROM services), 1));
SELECT setval(pg_get_serial_sequence('resources','id'),  GREATEST((SELECT max(id) FROM resources), 1));
