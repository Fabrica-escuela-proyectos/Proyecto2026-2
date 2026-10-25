-- ============================================================
-- Seguridad en BD (BD-07) — PROPUESTA. Rol de aplicación con privilegios mínimos sobre el esquema REAL (V1–V12).
-- Re-ejecutable. Ejecutar con el usuario DUEÑO de las tablas (el que hoy usa Flyway, p. ej. reservas_app en
-- docker-compose). Responsable BD: Juan Sebastián Andraus López.
--
-- Hoy la aplicación se conecta con el mismo usuario que es dueño del esquema y corre Flyway: ese usuario
-- puede DROP/TRUNCATE/ALTER. La propuesta separa:
--   · dueño / migraciones  → corre Flyway (DDL).            spring.flyway.user / spring.flyway.password
--   · reservas_runtime      → lo usa la app en ejecución.     spring.datasource.username / password
-- Ninguna contraseña va en el repo: se define con ALTER ROLE desde una consola y se pasa por variables de
-- entorno (DB_USERNAME/DB_PASSWORD y, nuevas, DB_MIGRATION_USERNAME/DB_MIGRATION_PASSWORD).
-- Requiere que el usuario que ejecuta pueda crear roles (CREATEROLE); si el proveedor no lo permite
-- (algunos planes gratuitos), crear el rol desde su panel y ejecutar desde el paso 2.
-- ============================================================

-- 1) Rol de ejecución, sin contraseña aquí (después: ALTER ROLE reservas_runtime PASSWORD '<secreto>';)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'reservas_runtime') THEN
        CREATE ROLE reservas_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
    END IF;
END $$;

-- 2) Nadie fuera del dueño crea objetos en public
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO reservas_runtime;

-- 3) Privilegios por tabla, según lo que hace el código (repositorios JPA de cada módulo)
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM reservas_runtime;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO reservas_runtime;
GRANT INSERT, UPDATE ON users, user_roles, sessions, mfa, providers, businesses,
                        services, resources, resource_availability, service_resources, bookings TO reservas_runtime;
GRANT INSERT ON audit_logs TO reservas_runtime;                            -- auditoría: solo agregar
GRANT DELETE ON users,                                                     -- HU-05 / HU-28 eliminar cuenta
                user_roles,                                                -- cambio de rol (reemplaza el conjunto)
                resource_availability,                                     -- HU-19 reemplazo del horario
                service_resources                                          -- HU-18 reemplazo de recursos
             TO reservas_runtime;
-- Sin DELETE en bookings, services, resources, businesses ni audit_logs: el historial no se borra desde la app.
-- Las cascadas y SET NULL al eliminar un usuario las ejecuta PostgreSQL como dueño de cada tabla,
-- así que reservas_runtime no necesita más permisos para HU-28 (verificado en pruebas_privilegios_sprint2.sql).
DO $$ BEGIN                                                               -- historial de Flyway: solo el dueño
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        REVOKE ALL ON flyway_schema_history FROM reservas_runtime;
    END IF;
END $$;

-- 4) Tablas futuras creadas por el dueño (nuevas migraciones): lectura y escritura básica; DELETE se decide caso a caso
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO reservas_runtime;

-- Resultado
SELECT 'privilegios de reservas_runtime aplicados' AS estado,
       has_table_privilege('reservas_runtime', 'public.bookings',   'DELETE')   AS borra_reservas,
       has_table_privilege('reservas_runtime', 'public.audit_logs', 'UPDATE')   AS modifica_auditoria,
       has_table_privilege('reservas_runtime', 'public.users',      'DELETE')   AS elimina_usuarios,
       has_schema_privilege('reservas_runtime', 'public',           'CREATE')   AS crea_tablas;
