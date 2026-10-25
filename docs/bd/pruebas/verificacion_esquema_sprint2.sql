-- ============================================================
-- Verificación del esquema real tras Flyway V1–V12 (solo lectura). Sirve para Render, local o Testcontainers.
-- Cada fila compara lo esperado por el modelo (docs/bd/modelo/modelo_sprint2.md) con lo que hay en la base.
-- ============================================================
SELECT n, verificacion, esperado, actual, CASE WHEN actual = esperado THEN 'OK' ELSE 'REVISAR' END AS estado
FROM (
  SELECT 1 AS n, 'Tablas del modelo V1–V12' AS verificacion, '13' AS esperado,
         (SELECT count(*)::text FROM pg_tables WHERE schemaname = 'public' AND tablename IN
            ('users','roles','user_roles','audit_logs','sessions','providers','businesses','mfa',
             'services','resources','service_resources','resource_availability','bookings')) AS actual
  UNION ALL SELECT 2, 'Tablas antiguas del modelo formal ausentes (reservations, resource_schedules, resource_blocks)', '0',
         (SELECT count(*)::text FROM pg_tables WHERE schemaname='public'
          AND tablename IN ('reservations','resource_schedules','resource_blocks'))
  UNION ALL SELECT 3, 'Última migración aplicada por Flyway (si existe el historial)', '12',
         (SELECT CASE WHEN to_regclass('public.flyway_schema_history') IS NULL THEN '12'
                      ELSE (xpath('/row/v/text()', query_to_xml(
                            'SELECT max(version::int) AS v FROM flyway_schema_history WHERE success', false, true, '')))[1]::text END)
  UNION ALL SELECT 4, 'Todas las PK id son UUID', '0',
         (SELECT count(*)::text FROM information_schema.columns
          WHERE table_schema='public' AND column_name='id' AND data_type <> 'uuid' AND table_name <> 'flyway_schema_history')
  UNION ALL SELECT 5, 'Columnas de fecha sin zona horaria (deben ser TIMESTAMPTZ)', '0',
         (SELECT count(*)::text FROM information_schema.columns
          WHERE table_schema='public' AND data_type = 'timestamp without time zone' AND table_name <> 'flyway_schema_history')
  UNION ALL SELECT 6, 'Extensión btree_gist', '1', (SELECT count(*)::text FROM pg_extension WHERE extname='btree_gist')
  UNION ALL SELECT 7, 'EXCLUDE anti-overbooking ex_bookings_no_overlap (ADR-005)', '1',
         (SELECT count(*)::text FROM pg_constraint WHERE conname='ex_bookings_no_overlap' AND contype='x')
  UNION ALL SELECT 8, 'FK hacia users con RESTRICT/NO ACTION (convención: ninguna)', '0',
         (SELECT count(*)::text FROM pg_constraint WHERE contype='f' AND confrelid='public.users'::regclass AND confdeltype IN ('r','a'))
  UNION ALL SELECT 9, 'FK de bookings con SET NULL (client, business, service, resource; ADR-006)', '4',
         (SELECT count(*)::text FROM pg_constraint WHERE contype='f' AND conrelid='public.bookings'::regclass AND confdeltype='n')
  UNION ALL SELECT 10, 'Copias obligatorias en bookings (client_email/name, business/service/resource_name, price_cop)', '6',
         (SELECT count(*)::text FROM information_schema.columns WHERE table_name='bookings' AND is_nullable='NO'
          AND column_name IN ('client_email','client_name','business_name','service_name','resource_name','price_cop'))
  UNION ALL SELECT 11, 'CHECK de bookings (rango, estado, precio, origen)', '4',
         (SELECT count(*)::text FROM pg_constraint WHERE conrelid='public.bookings'::regclass AND contype='c')
  UNION ALL SELECT 12, 'Únicos sin mayúsculas (servicio y recurso por negocio)', '2',
         (SELECT count(*)::text FROM pg_indexes WHERE indexname IN ('uk_services_business_name','uk_resources_business_name'))
  UNION ALL SELECT 13, 'Índices de reservas (cliente, negocio, recurso)', '3',
         (SELECT count(*)::text FROM pg_indexes WHERE indexname IN
            ('idx_bookings_client_id','idx_bookings_business_id','idx_bookings_resource_start'))
  UNION ALL SELECT 14, 'businesses.min_advance_hours con CHECK 1–720 (HU-08)', '1',
         (SELECT count(*)::text FROM pg_constraint WHERE conname='ck_businesses_min_advance_hours')
  UNION ALL SELECT 15, 'Roles del catálogo (CLIENTE, PROVEEDOR, ADMINISTRADOR)', '3',
         (SELECT count(*)::text FROM roles WHERE name IN ('CLIENTE','PROVEEDOR','ADMINISTRADOR'))
  UNION ALL SELECT 16, 'Reservas CONFIRMADAS traslapadas en el mismo recurso (debe ser 0)', '0',
         (SELECT count(*)::text FROM bookings a JOIN bookings b ON a.resource_id = b.resource_id AND a.id < b.id
          WHERE a.status='CONFIRMADA' AND b.status='CONFIRMADA'
            AND tstzrange(a.start_at, a.end_at) && tstzrange(b.start_at, b.end_at))
  UNION ALL SELECT 17, 'Reservas CANCELADAS sin cancelled_at (calidad de datos)', '0',
         (SELECT count(*)::text FROM bookings WHERE status='CANCELADA' AND cancelled_at IS NULL)
  UNION ALL SELECT 18, 'Horarios traslapados en un mismo recurso y día (los evita la app)', '0',
         (SELECT count(*)::text FROM resource_availability a JOIN resource_availability b
            ON a.resource_id = b.resource_id AND a.day_of_week = b.day_of_week AND a.id < b.id
          WHERE a.start_time < b.end_time AND b.start_time < a.end_time)
) v
ORDER BY n;
