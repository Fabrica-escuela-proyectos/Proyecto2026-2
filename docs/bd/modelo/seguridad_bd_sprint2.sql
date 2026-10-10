-- ============================================================
-- Seguridad BD Sprint 2 (PostgreSQL / Supabase). Re-ejecutable.
-- Objetivo: la API pública de Supabase (anon/authenticated) no accede a las tablas;
--           solo el usuario de aplicación (app_user, mínimo privilegio) lo hace.
-- ============================================================

-- 1) RLS activado en todas las tablas del esquema public
DO $$
DECLARE t TEXT;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
    END LOOP;
END $$;

-- 2) app_user conserva acceso (los GRANT ya limitan operaciones); sin política un rol no dueño vería 0 filas
DO $$
DECLARE t TEXT;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
            EXECUTE format('DROP POLICY IF EXISTS app_user_all ON public.%I', t);
            EXECUTE format('CREATE POLICY app_user_all ON public.%I FOR ALL TO app_user USING (true) WITH CHECK (true)', t);
        END LOOP;
    END IF;
END $$;

-- 3) Cerrar la API pública de Supabase (si esos roles existen)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon;
        REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON ALL TABLES IN SCHEMA public FROM authenticated;
        REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM authenticated;
    END IF;
END $$;

-- 4) Auditoría inmutable para la aplicación: solo INSERT/SELECT (ya otorgado en Sprint 1); verificar
REVOKE UPDATE, DELETE ON audit_logs FROM app_user;

-- 5) Cambiar la contraseña por defecto del script de Sprint 1 (ejecutar manualmente con un secreto real):
--    ALTER ROLE app_user PASSWORD '<secreto-en-variable-de-entorno>';
