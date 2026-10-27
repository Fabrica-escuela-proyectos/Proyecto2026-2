-- ============================================================
-- Pruebas de la propuesta de seguridad (BD-07): qué puede y qué no puede hacer reservas_runtime.
-- psql en BD LOCAL, con superusuario (usa SET ROLE). Requiere V1–V12, docs/bd/scripts/seguridad_bd_sprint2.sql
-- y docs/bd/pruebas/datos_semilla_sprint2.sql. Todo con ROLLBACK.
-- ============================================================
BEGIN;
CREATE TEMP TABLE r (caso TEXT, ok BOOLEAN);
GRANT INSERT ON r TO reservas_runtime;
SET ROLE reservas_runtime;

-- Lo que la app necesita
DO $$ BEGIN
  INSERT INTO users (id, full_name, email, phone_number, password_hash)
  VALUES ('e1000000-0000-0000-0000-000000000001','priv','priv@prueba.test','9811111111','x');
  INSERT INTO bookings (client_id, client_email, client_name, business_id, business_name, service_id, service_name,
                        resource_id, resource_name, start_at, end_at, price_cop)
  VALUES ('e1000000-0000-0000-0000-000000000001','priv@prueba.test','priv','30000000-0000-0000-0000-000000000001','Centro Deportivo Demo',
          '40000000-0000-0000-0000-000000000001','Alquiler de sala','50000000-0000-0000-0000-000000000002','Sala 2',
          '2030-03-05 09:00-05','2030-03-05 10:00-05', 80000);
  INSERT INTO audit_logs (event_type, subject_email, outcome) VALUES ('CREACION_RESERVA','priv@prueba.test','SUCCESS');
  INSERT INTO r VALUES ('P1 crear usuario, reserva y evento de auditoría', TRUE);
EXCEPTION WHEN OTHERS THEN INSERT INTO r VALUES ('P1 [' || SQLERRM || ']', FALSE); END $$;

DO $$ BEGIN
  UPDATE bookings SET status='CANCELADA', cancel_origin='ELIMINACION_CUENTA', cancelled_at=now()
  WHERE client_id='e1000000-0000-0000-0000-000000000001' AND status='CONFIRMADA' AND start_at > now();
  DELETE FROM users WHERE id='e1000000-0000-0000-0000-000000000001';
  INSERT INTO r VALUES ('P2 HU-28 eliminar cuenta (cascada y SET NULL sin permisos extra)',
    (SELECT client_id IS NULL AND status='CANCELADA' FROM bookings WHERE client_email='priv@prueba.test'));
EXCEPTION WHEN OTHERS THEN INSERT INTO r VALUES ('P2 [' || SQLERRM || ']', FALSE); END $$;

DO $$ BEGIN
  DELETE FROM service_resources WHERE service_id='40000000-0000-0000-0000-000000000002';
  INSERT INTO service_resources (service_id, resource_id) VALUES ('40000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000003');
  DELETE FROM resource_availability WHERE resource_id='50000000-0000-0000-0000-000000000003' AND day_of_week=7;
  INSERT INTO r VALUES ('P3 HU-18/HU-19 reemplazar recursos y horario', TRUE);
EXCEPTION WHEN OTHERS THEN INSERT INTO r VALUES ('P3 [' || SQLERRM || ']', FALSE); END $$;

-- Lo que NO debe poder hacer
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'UPDATE audit_logs SET outcome = ''X''',
    'DELETE FROM audit_logs',
    'DELETE FROM bookings',
    'DELETE FROM services',
    'INSERT INTO roles (name) VALUES (''ROOT'')',
    'TRUNCATE bookings',
    'DROP TABLE bookings',
    'ALTER TABLE bookings ADD COLUMN x INT',
    'CREATE TABLE intrusa (id INT)',
    'SELECT * FROM flyway_schema_history'] LOOP
    BEGIN
      EXECUTE t;
      INSERT INTO r VALUES ('P4 prohibido: ' || t || ' [se permitió]', FALSE);
    EXCEPTION WHEN insufficient_privilege OR undefined_table THEN
      INSERT INTO r VALUES ('P4 prohibido: ' || t, TRUE);
    WHEN OTHERS THEN
      INSERT INTO r VALUES ('P4 prohibido: ' || t || ' [' || SQLERRM || ']', SQLERRM ILIKE '%must be owner%' OR SQLERRM ILIKE '%permission denied%');
    END;
  END LOOP;
END $$;

RESET ROLE;
SELECT caso, CASE WHEN ok THEN 'OK' ELSE 'FALLO' END AS resultado FROM r ORDER BY caso;
ROLLBACK;
