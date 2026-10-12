-- ============================================================
-- Pruebas de integridad de la BD — Sprint 2 (BD-04, BD-06) sobre el esquema REAL (Flyway V1–V12).
-- Verifican lo que la BASE garantiza por sí sola (constraints, EXCLUDE, ON DELETE), aunque la app se salte.
-- Las reglas que valida la aplicación (horario, antelación, traslape de horarios, mismo negocio) están
-- cubiertas por las pruebas de integración del backend; aquí solo se documenta que la BD no las bloquea.
-- Compatible con psql y con el SQL Editor (sin comandos psql). Re-ejecutable. Usa UUID f…; limpia al
-- inicio y al final. Ejecutar SOLO en una BD local o de pruebas. Resultado: tabla de casos + total.
-- Responsable BD: Juan Sebastián Andraus López.
-- ============================================================

CREATE OR REPLACE FUNCTION pg_temp.limpiar() RETURNS VOID AS $f$
BEGIN
    DELETE FROM bookings WHERE client_email LIKE 'zz%@prueba.test' OR business_name LIKE 'zz %';
    DELETE FROM users    WHERE email LIKE 'zz%@prueba.test';  -- CASCADE: providers → businesses → services/resources → …
END $f$ LANGUAGE plpgsql;
SELECT pg_temp.limpiar();

-- ---------- Datos base ----------
INSERT INTO users (id, full_name, email, phone_number, password_hash) VALUES
 ('f1000000-0000-0000-0000-000000000001','zz cliente',    'zz1@prueba.test','9900000001','x'),
 ('f1000000-0000-0000-0000-000000000002','zz proveedor',  'zz2@prueba.test','9900000002','x'),
 ('f1000000-0000-0000-0000-000000000003','zz proveedor 2','zz3@prueba.test','9900000003','x'),
 ('f1000000-0000-0000-0000-000000000005','zz cliente 2',  'zz5@prueba.test','9900000005','x');
INSERT INTO user_roles (user_id, role_id)
SELECT v.u::uuid, r.id FROM (VALUES ('f1000000-0000-0000-0000-000000000001','CLIENTE'),
    ('f1000000-0000-0000-0000-000000000002','PROVEEDOR'), ('f1000000-0000-0000-0000-000000000003','PROVEEDOR'),
    ('f1000000-0000-0000-0000-000000000005','CLIENTE')) v(u, rol) JOIN roles r ON r.name = v.rol;
INSERT INTO providers (id, user_id) VALUES
 ('f2000000-0000-0000-0000-000000000001','f1000000-0000-0000-0000-000000000002'),
 ('f2000000-0000-0000-0000-000000000002','f1000000-0000-0000-0000-000000000003');
INSERT INTO businesses (id, provider_id, name) VALUES
 ('f3000000-0000-0000-0000-000000000001','f2000000-0000-0000-0000-000000000001','zz negocio 1'),
 ('f3000000-0000-0000-0000-000000000002','f2000000-0000-0000-0000-000000000002','zz negocio 2');
-- created_by queda NULL a propósito: todo el script corre en UNA transacción y, si el creador se borra en la
-- misma transacción en que se creó el servicio, PostgreSQL rechaza la cascada (hallazgo H-01, caso T37).
INSERT INTO services (id, business_id, name, duration_minutes, price_cop) VALUES
 ('f4000000-0000-0000-0000-000000000001','f3000000-0000-0000-0000-000000000001','Sala hora',  60, 50000),
 ('f4000000-0000-0000-0000-000000000002','f3000000-0000-0000-0000-000000000001','Media hora', 30, 25000);
INSERT INTO resources (id, business_id, name, type) VALUES
 ('f5000000-0000-0000-0000-000000000001','f3000000-0000-0000-0000-000000000001','Sala A','SALA'),
 ('f5000000-0000-0000-0000-000000000002','f3000000-0000-0000-0000-000000000001','Sala B','SALA');
INSERT INTO service_resources (service_id, resource_id) VALUES
 ('f4000000-0000-0000-0000-000000000001','f5000000-0000-0000-0000-000000000001'),
 ('f4000000-0000-0000-0000-000000000002','f5000000-0000-0000-0000-000000000002');
INSERT INTO resource_availability (resource_id, day_of_week, start_time, end_time)
SELECT r::uuid, d, '08:00', '18:00' FROM generate_series(1,7) d,
       (VALUES ('f5000000-0000-0000-0000-000000000001'),('f5000000-0000-0000-0000-000000000002')) v(r);

-- Reserva de prueba: devuelve el INSERT con todas las copias (servicio 'Sala hora' o NULL si ya se borró)
CREATE OR REPLACE FUNCTION pg_temp.reserva(p_id TEXT, p_cliente TEXT, p_recurso TEXT, p_ini TEXT, p_fin TEXT,
                                           p_estado TEXT DEFAULT 'CONFIRMADA') RETURNS TEXT AS $f$
SELECT format($$INSERT INTO bookings (id, client_id, client_email, client_name, business_id, business_name,
                   service_id, service_name, resource_id, resource_name, start_at, end_at, status, price_cop, cancelled_at, cancel_origin)
                 VALUES (%s, %L, (SELECT email FROM users WHERE id = %2$L), 'zz', 'f3000000-0000-0000-0000-000000000001', 'zz negocio 1',
                   (SELECT id FROM services WHERE id = 'f4000000-0000-0000-0000-000000000001'), 'Sala hora', %s, 'zz recurso', %L, %L, %L, 50000,
                   CASE WHEN %6$L = 'CANCELADA' THEN now() END, CASE WHEN %6$L = 'CANCELADA' THEN 'CLIENTE' END)$$,
              COALESCE(quote_literal(p_id), 'gen_random_uuid()'), p_cliente,
              COALESCE(quote_literal(p_recurso), 'NULL'), p_ini, p_fin, p_estado);
$f$ LANGUAGE sql;

DROP TABLE IF EXISTS resultados_pruebas;
CREATE TEMP TABLE resultados_pruebas (n SERIAL, caso TEXT, ok BOOLEAN);

CREATE OR REPLACE FUNCTION pg_temp.ok(p_caso TEXT, p_sql TEXT) RETURNS VOID AS $f$
BEGIN
    EXECUTE p_sql;
    INSERT INTO resultados_pruebas (caso, ok) VALUES (p_caso, TRUE);
EXCEPTION WHEN OTHERS THEN
    INSERT INTO resultados_pruebas (caso, ok) VALUES (p_caso || ' [' || SQLERRM || ']', FALSE);
END $f$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION pg_temp.error(p_caso TEXT, p_sql TEXT, p_patron TEXT) RETURNS VOID AS $f$
BEGIN
    EXECUTE p_sql;
    INSERT INTO resultados_pruebas (caso, ok) VALUES (p_caso || ' [no falló]', FALSE);
EXCEPTION WHEN OTHERS THEN
    INSERT INTO resultados_pruebas (caso, ok)
    VALUES (p_caso || CASE WHEN SQLERRM ILIKE '%' || p_patron || '%' THEN '' ELSE ' [' || SQLERRM || ']' END,
            SQLERRM ILIKE '%' || p_patron || '%');
END $f$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION pg_temp.verdad(p_caso TEXT, p_sql TEXT) RETURNS VOID AS $f$
DECLARE v BOOLEAN;
BEGIN
    EXECUTE p_sql INTO v;
    INSERT INTO resultados_pruebas (caso, ok) VALUES (p_caso || CASE WHEN v THEN '' ELSE ' [falso]' END, COALESCE(v, FALSE));
EXCEPTION WHEN OTHERS THEN
    INSERT INTO resultados_pruebas (caso, ok) VALUES (p_caso || ' [' || SQLERRM || ']', FALSE);
END $f$ LANGUAGE plpgsql;

-- ---------- Casos ----------
DO $run$
DECLARE
    c1 CONSTANT TEXT := 'f1000000-0000-0000-0000-000000000001';
    ra CONSTANT TEXT := 'f5000000-0000-0000-0000-000000000001';
    rb CONSTANT TEXT := 'f5000000-0000-0000-0000-000000000002';
BEGIN
  -- Anti-overbooking (ADR-005, BD-04)
  PERFORM pg_temp.ok('T01 HU-22 reserva válida con copias', pg_temp.reserva('f6000000-0000-0000-0000-000000000001', c1, ra, '2030-03-05 09:00-05', '2030-03-05 10:00-05'));
  PERFORM pg_temp.error('T02 ADR-005 D1 traslape en el mismo recurso', pg_temp.reserva(NULL, c1, ra, '2030-03-05 09:30-05', '2030-03-05 10:30-05'), 'ex_bookings_no_overlap');
  PERFORM pg_temp.error('T03 ADR-005 D1 reserva que contiene a otra', pg_temp.reserva(NULL, c1, ra, '2030-03-05 08:00-05', '2030-03-05 11:00-05'), 'ex_bookings_no_overlap');
  PERFORM pg_temp.ok('T04 ADR-005 rango semiabierto: contigua 10:00–11:00 se permite', pg_temp.reserva(NULL, c1, ra, '2030-03-05 10:00-05', '2030-03-05 11:00-05'));
  PERFORM pg_temp.ok('T05 ADR-005 D2 mismo horario en otro recurso se permite', pg_temp.reserva(NULL, c1, rb, '2030-03-05 09:00-05', '2030-03-05 10:00-05'));
  PERFORM pg_temp.ok('T06 una CANCELADA no ocupa el horario', pg_temp.reserva(NULL, c1, ra, '2030-03-05 13:00-05', '2030-03-05 14:00-05', 'CANCELADA')
                     || '; ' || pg_temp.reserva(NULL, c1, ra, '2030-03-05 13:00-05', '2030-03-05 14:00-05'));
  PERFORM pg_temp.ok('T07 una COMPLETADA no ocupa el horario (WHERE status = CONFIRMADA)', pg_temp.reserva(NULL, c1, ra, '2030-03-05 15:00-05', '2030-03-05 16:00-05', 'COMPLETADA')
                     || '; ' || pg_temp.reserva(NULL, c1, ra, '2030-03-05 15:00-05', '2030-03-05 16:00-05'));
  PERFORM pg_temp.ok('T08 histórico con resource_id NULL no participa del EXCLUDE', pg_temp.reserva(NULL, c1, NULL, '2030-03-05 09:00-05', '2030-03-05 10:00-05')
                     || '; ' || pg_temp.reserva(NULL, c1, NULL, '2030-03-05 09:00-05', '2030-03-05 10:00-05'));
  PERFORM pg_temp.error('T09 reactivar una cancelada que choca con otra confirmada',
    $$UPDATE bookings SET status='CONFIRMADA', cancelled_at=NULL, cancel_origin=NULL
      WHERE status='CANCELADA' AND client_email='zz1@prueba.test' AND start_at='2030-03-05 13:00-05'$$, 'ex_bookings_no_overlap');

  -- CHECK de bookings
  PERFORM pg_temp.error('T10 fin <= inicio', pg_temp.reserva(NULL, c1, rb, '2030-03-06 10:00-05', '2030-03-06 09:00-05'), 'ck_bookings_range');
  PERFORM pg_temp.error('T11 estado inválido', pg_temp.reserva(NULL, c1, rb, '2030-03-06 10:00-05', '2030-03-06 11:00-05', 'PENDIENTE'), 'ck_bookings_status');
  PERFORM pg_temp.error('T12 HU-25 origen de cancelación inválido',
    $$UPDATE bookings SET cancel_origin='ADMIN' WHERE id='f6000000-0000-0000-0000-000000000001'$$, 'ck_bookings_cancel_origin');
  PERFORM pg_temp.error('T13 precio negativo',
    $$UPDATE bookings SET price_cop=-1 WHERE id='f6000000-0000-0000-0000-000000000001'$$, 'ck_bookings_price');
  PERFORM pg_temp.error('T14 HU-24 copia client_name obligatoria',
    $$UPDATE bookings SET client_name=NULL WHERE id='f6000000-0000-0000-0000-000000000001'$$, 'client_name');

  -- Catálogo (HU-08, 09, 14, 18, 19)
  PERFORM pg_temp.error('T15 HU-08 antelación 0 h', $$UPDATE businesses SET min_advance_hours=0 WHERE id='f3000000-0000-0000-0000-000000000001'$$, 'ck_businesses_min_advance_hours');
  PERFORM pg_temp.error('T16 HU-08 antelación 721 h', $$UPDATE businesses SET min_advance_hours=721 WHERE id='f3000000-0000-0000-0000-000000000001'$$, 'ck_businesses_min_advance_hours');
  PERFORM pg_temp.verdad('T17 HU-08 antelación por defecto = 1 h',
    $$SELECT min_advance_hours = 1 FROM businesses WHERE id='f3000000-0000-0000-0000-000000000002'$$);
  PERFORM pg_temp.error('T18 HU-09 nombre de servicio repetido (sin distinguir mayúsculas)',
    $$INSERT INTO services (business_id,name,duration_minutes,price_cop) VALUES ('f3000000-0000-0000-0000-000000000001','SALA HORA',60,1)$$, 'uk_services_business_name');
  PERFORM pg_temp.ok('T19 HU-09 mismo nombre en otro negocio',
    $$INSERT INTO services (business_id,name,duration_minutes,price_cop) VALUES ('f3000000-0000-0000-0000-000000000002','Sala hora',60,1)$$);
  PERFORM pg_temp.error('T20 HU-09 duración 0', $$INSERT INTO services (business_id,name,duration_minutes,price_cop) VALUES ('f3000000-0000-0000-0000-000000000001','X',0,1)$$, 'ck_services_duration');
  PERFORM pg_temp.error('T21 HU-09 precio negativo', $$INSERT INTO services (business_id,name,duration_minutes,price_cop) VALUES ('f3000000-0000-0000-0000-000000000001','Y',30,-5)$$, 'ck_services_price');
  PERFORM pg_temp.error('T22 HU-14 tipo de recurso fuera de SALA/EQUIPO/PERSONAL',
    $$INSERT INTO resources (business_id,name,type) VALUES ('f3000000-0000-0000-0000-000000000001','Cancha','CANCHA')$$, 'ck_resources_type');
  PERFORM pg_temp.error('T23 HU-14 nombre de recurso repetido',
    $$INSERT INTO resources (business_id,name,type) VALUES ('f3000000-0000-0000-0000-000000000001','sala a','SALA')$$, 'uk_resources_business_name');
  PERFORM pg_temp.error('T24 HU-18 asignación duplicada',
    $$INSERT INTO service_resources (service_id,resource_id) VALUES ('f4000000-0000-0000-0000-000000000001','f5000000-0000-0000-0000-000000000001')$$, 'service_resources_pkey');
  PERFORM pg_temp.error('T25 HU-19 día 8', $$INSERT INTO resource_availability (resource_id,day_of_week,start_time,end_time) VALUES ('f5000000-0000-0000-0000-000000000001',8,'08:00','09:00')$$, 'ck_resource_availability_day');
  PERFORM pg_temp.error('T26 HU-19 inicio >= fin', $$INSERT INTO resource_availability (resource_id,day_of_week,start_time,end_time) VALUES ('f5000000-0000-0000-0000-000000000001',1,'10:00','10:00')$$, 'ck_resource_availability_range');
  PERFORM pg_temp.ok('T27 (documenta) la BD acepta horarios traslapados: los rechaza la app con bloqueo de fila',
    $$INSERT INTO resource_availability (resource_id,day_of_week,start_time,end_time) VALUES ('f5000000-0000-0000-0000-000000000001',1,'09:00','10:00');
      DELETE FROM resource_availability WHERE resource_id='f5000000-0000-0000-0000-000000000001' AND day_of_week=1 AND start_time='09:00'$$);

  -- HU-18: el historial no bloquea reemplazar recursos
  PERFORM pg_temp.ok('T28 HU-18 reemplazar recursos de un servicio con reservas existentes',
    $$DELETE FROM service_resources WHERE service_id='f4000000-0000-0000-0000-000000000001';
      INSERT INTO service_resources VALUES ('f4000000-0000-0000-0000-000000000001','f5000000-0000-0000-0000-000000000002', now())$$);

  -- HU-16: cancelación masiva por recurso (consulta del backend)
  PERFORM pg_temp.ok('T29a reserva pasada confirmada (no se debe tocar)', pg_temp.reserva('f6000000-0000-0000-0000-000000000009', c1, rb, '2020-01-01 09:00-05', '2020-01-01 10:00-05'));
  PERFORM pg_temp.verdad('T29b HU-16 cancela solo futuras CONFIRMADAS del recurso con origen RECURSO_NO_DISPONIBLE',
    $$WITH u AS (UPDATE bookings SET status='CANCELADA', cancel_origin='RECURSO_NO_DISPONIBLE', cancelled_at=now()
                 WHERE resource_id='f5000000-0000-0000-0000-000000000002' AND status='CONFIRMADA' AND start_at > now() RETURNING 1)
      SELECT (SELECT count(*) FROM u) = 1
         AND (SELECT status FROM bookings WHERE id='f6000000-0000-0000-0000-000000000009') = 'CONFIRMADA'$$);

  -- Eliminación física de servicio y recurso: SET NULL + copias
  PERFORM pg_temp.verdad('T30 borrar servicio → bookings.service_id NULL, service_name intacto, asignaciones en cascada',
    $$WITH d AS (DELETE FROM services WHERE id='f4000000-0000-0000-0000-000000000001' RETURNING 1) SELECT count(*) = 1 FROM d$$);
  PERFORM pg_temp.verdad('T30b verificación',
    $$SELECT (SELECT service_id IS NULL AND service_name='Sala hora' FROM bookings WHERE id='f6000000-0000-0000-0000-000000000001')
         AND NOT EXISTS (SELECT 1 FROM service_resources WHERE service_id='f4000000-0000-0000-0000-000000000001')$$);
  PERFORM pg_temp.verdad('T31 borrar recurso → bookings.resource_id NULL, resource_name intacto, horario en cascada',
    $$WITH d AS (DELETE FROM resources WHERE id='f5000000-0000-0000-0000-000000000001' RETURNING 1)
      SELECT (SELECT count(*) FROM d) = 1$$);
  PERFORM pg_temp.verdad('T31b verificación',
    $$SELECT (SELECT resource_id IS NULL AND resource_name='zz recurso' FROM bookings WHERE id='f6000000-0000-0000-0000-000000000001')
         AND NOT EXISTS (SELECT 1 FROM resource_availability WHERE resource_id='f5000000-0000-0000-0000-000000000001')$$);

  -- HU-28: eliminar cliente
  INSERT INTO sessions (user_id, token_id, expires_at) VALUES ('f1000000-0000-0000-0000-000000000005','zz-jti-5', now() + INTERVAL '1 hour');
  INSERT INTO mfa (user_id, secret) VALUES ('f1000000-0000-0000-0000-000000000005','ZZSECRET');
  PERFORM pg_temp.ok('T32a reserva futura del cliente a eliminar',
    pg_temp.reserva('f6000000-0000-0000-0000-000000000007', 'f1000000-0000-0000-0000-000000000005', 'f5000000-0000-0000-0000-000000000002', '2030-03-07 09:00-05', '2030-03-07 10:00-05'));
  PERFORM pg_temp.ok('T32b HU-28 cancelar futuras (ELIMINACION_CUENTA) y borrar el usuario',
    $$UPDATE bookings SET status='CANCELADA', cancel_origin='ELIMINACION_CUENTA', cancelled_at=now()
      WHERE client_id='f1000000-0000-0000-0000-000000000005' AND status='CONFIRMADA' AND start_at > now();
      DELETE FROM users WHERE id='f1000000-0000-0000-0000-000000000005'$$);
  PERFORM pg_temp.verdad('T32c HU-28 reserva conservada: CANCELADA, client_id NULL, correo y nombre copiados',
    $$SELECT status='CANCELADA' AND cancel_origin='ELIMINACION_CUENTA' AND client_id IS NULL AND client_email='zz5@prueba.test'
      FROM bookings WHERE id='f6000000-0000-0000-0000-000000000007'$$);
  PERFORM pg_temp.verdad('T32d HU-28 sesiones, MFA y roles del usuario eliminados en cascada',
    $$SELECT NOT EXISTS (SELECT 1 FROM sessions WHERE token_id='zz-jti-5')
         AND NOT EXISTS (SELECT 1 FROM mfa WHERE user_id='f1000000-0000-0000-0000-000000000005')
         AND NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id='f1000000-0000-0000-0000-000000000005')$$);

  -- HU-28: eliminar proveedor (sus negocios, servicios y recursos se van; las reservas quedan)
  PERFORM pg_temp.ok('T33a HU-28 borrar proveedor con negocio, servicios, recursos y reservas',
    $$DELETE FROM users WHERE id='f1000000-0000-0000-0000-000000000002'$$);
  PERFORM pg_temp.verdad('T33b HU-28 negocio, servicios, recursos y horarios eliminados en cascada',
    $$SELECT NOT EXISTS (SELECT 1 FROM businesses WHERE id='f3000000-0000-0000-0000-000000000001')
         AND NOT EXISTS (SELECT 1 FROM services  WHERE business_id='f3000000-0000-0000-0000-000000000001')
         AND NOT EXISTS (SELECT 1 FROM resources WHERE id='f5000000-0000-0000-0000-000000000002')
         AND NOT EXISTS (SELECT 1 FROM resource_availability WHERE resource_id='f5000000-0000-0000-0000-000000000002')$$);
  PERFORM pg_temp.verdad('T33c ADR-006 D2 las reservas del negocio siguen (business/service/resource en NULL, copias intactas)',
    $$SELECT count(*) > 0 AND bool_and(business_id IS NULL AND resource_id IS NULL AND business_name='zz negocio 1')
      FROM bookings WHERE business_name='zz negocio 1'$$);

  -- Identidad (regresión Sprint 1)
  PERFORM pg_temp.error('T34 correo repetido', $$INSERT INTO users (full_name,email,phone_number,password_hash) VALUES ('x','zz1@prueba.test','9900000099','x')$$, 'uk_users_email');
  PERFORM pg_temp.error('T35 celular repetido', $$INSERT INTO users (full_name,email,phone_number,password_hash) VALUES ('x','zz9@prueba.test','9900000001','x')$$, 'uk_users_phone');
  PERFORM pg_temp.error('T36 un solo registro de MFA por usuario',
    $$INSERT INTO mfa (user_id,secret) VALUES ('f1000000-0000-0000-0000-000000000001','A'), ('f1000000-0000-0000-0000-000000000001','B')$$, 'uk_mfa_user_id');

  -- Hallazgo H-01: borrar al proveedor en la MISMA transacción en que creó un servicio con created_by.
  -- Sin la propuesta V13 falla (23503); con V13 (FK diferida) funciona. Con datos de transacciones
  -- anteriores (caso real de HU-28) funciona siempre: ver T33.
  IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'services_business_id_fkey' AND condeferred) THEN
    PERFORM pg_temp.ok('T37 (H-01, con V13) proveedor y su servicio creados y borrados en la misma transacción',
      $$INSERT INTO services (business_id,name,duration_minutes,price_cop,created_by)
          VALUES ('f3000000-0000-0000-0000-000000000002','H01',30,1,'f1000000-0000-0000-0000-000000000003');
        DELETE FROM users WHERE id='f1000000-0000-0000-0000-000000000003'$$);
  ELSE
    PERFORM pg_temp.error('T37 (H-01, sin V13) proveedor y su servicio creados y borrados en la misma transacción falla',
      $$INSERT INTO services (business_id,name,duration_minutes,price_cop,created_by)
          VALUES ('f3000000-0000-0000-0000-000000000002','H01',30,1,'f1000000-0000-0000-0000-000000000003');
        DELETE FROM users WHERE id='f1000000-0000-0000-0000-000000000003'$$, 'services_business_id_fkey');
  END IF;
END $run$;

SELECT pg_temp.limpiar();

-- Resultado (última sentencia: es lo que muestra el SQL Editor)
SELECT caso, CASE WHEN ok THEN 'OK' ELSE 'FALLO' END AS resultado
FROM (SELECT n, caso, ok FROM resultados_pruebas
      UNION ALL
      SELECT 9999, '== TOTAL: ' || count(*) FILTER (WHERE ok) || ' OK, ' || count(*) FILTER (WHERE NOT ok) || ' FALLO', bool_and(ok)
      FROM resultados_pruebas) t
ORDER BY n;
