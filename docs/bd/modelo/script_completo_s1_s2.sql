-- ============================================================
-- Script inicial PostgreSQL — Sprint 1 (Identidad y Acceso)
-- Responsable: ANDRAUS LOPEZ JUAN SEBASTIAN
-- ============================================================

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(150) NOT NULL,
    email           VARCHAR(150) NOT NULL UNIQUE,
    phone           VARCHAR(30)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE user_roles (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    role_id     BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at  TIMESTAMPTZ,
    PRIMARY KEY (user_id, role_id)
);

-- Un solo rol activo por usuario (LoginResponse.role / PATCH .../role)
CREATE UNIQUE INDEX idx_user_roles_active ON user_roles(user_id) WHERE revoked_at IS NULL;

CREATE TABLE permissions (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE providers (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    tax_id     VARCHAR(50) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE businesses (
    id          BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES providers(id) ON DELETE RESTRICT,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sessions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(255),
    CONSTRAINT chk_session_max_1h CHECK (expires_at <= created_at + interval '1 hour')
);

CREATE TABLE mfa (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    secret     VARCHAR(255) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    action     VARCHAR(50) NOT NULL,
    entity     VARCHAR(50),
    entity_id  BIGINT,
    metadata   JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Índices
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_expires_at ON sessions(expires_at);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
CREATE INDEX idx_audit_logs_action_ip_created ON audit_logs(action, ip_address, created_at);
CREATE INDEX idx_businesses_provider_id ON businesses(provider_id);

-- Bloquear cambio de rol de un PROVEEDOR (HU05, endpoints-sprint-1.md §6)
CREATE OR REPLACE FUNCTION fn_block_provider_role_change() RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM providers WHERE user_id = NEW.user_id) THEN
        RAISE EXCEPTION 'No se permite modificar el rol de un usuario PROVEEDOR';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_block_provider_role_change
    BEFORE INSERT OR UPDATE ON user_roles
    FOR EACH ROW EXECUTE FUNCTION fn_block_provider_role_change();

-- Roles base
INSERT INTO roles (name, description) VALUES
    ('CLIENTE', 'Usuario que consume el servicio'),
    ('PROVEEDOR', 'Usuario que ofrece un negocio'),
    ('ADMIN', 'Usuario administrador de la plataforma');

-- Seguridad: rol de aplicación con privilegio mínimo
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='app_user') THEN CREATE ROLE app_user LOGIN PASSWORD 'CHANGE_ME'; END IF; END $$;
GRANT SELECT, INSERT, UPDATE ON
    users, roles, user_roles, permissions, role_permissions,
    providers, businesses, sessions, mfa
    TO app_user;
GRANT SELECT, INSERT ON audit_logs TO app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user;

-- ===================== SPRINT 2 =====================
-- ============================================================
-- Script Sprint 2 (incremental sobre Sprint 1) — PostgreSQL / Supabase
-- Responsable: ANDRAUS LOPEZ JUAN SEBASTIAN
-- Requiere: esquema Sprint 1 ya aplicado (users, businesses, sessions, ...)
-- Re-ejecutable: usa IF NOT EXISTS / OR REPLACE / DROP TRIGGER IF EXISTS
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Rango de horas del día (horarios semanales sin traslape)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'timerange') THEN
        CREATE TYPE timerange AS RANGE (subtype = time);
    END IF;
END $$;

-- ------------------------------------------------------------
-- 1) Ajuste Sprint 1: MFA por sesión para operaciones sensibles
-- ------------------------------------------------------------
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS mfa_verified_at TIMESTAMPTZ;

-- ------------------------------------------------------------
-- 2) Tablas
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS services (
    id               BIGSERIAL PRIMARY KEY,
    business_id      BIGINT NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    name             VARCHAR(150) NOT NULL,
    description      VARCHAR(500),
    duration_minutes INT NOT NULL CHECK (duration_minutes BETWEEN 5 AND 480),
    price            NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_services_id_business UNIQUE (id, business_id)
);

CREATE TABLE IF NOT EXISTS resources (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_resources_id_business UNIQUE (id, business_id)
);

-- Qué recursos pueden prestar qué servicio (mismo negocio garantizado por FK compuesta)
CREATE TABLE IF NOT EXISTS service_resources (
    service_id  BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    PRIMARY KEY (service_id, resource_id),
    CONSTRAINT fk_sr_service  FOREIGN KEY (service_id, business_id)  REFERENCES services(id, business_id)  ON DELETE RESTRICT,
    CONSTRAINT fk_sr_resource FOREIGN KEY (resource_id, business_id) REFERENCES resources(id, business_id) ON DELETE RESTRICT
);

-- Horario semanal de un recurso (day_of_week ISO: 1=lunes ... 7=domingo)
CREATE TABLE IF NOT EXISTS resource_schedules (
    id          BIGSERIAL PRIMARY KEY,
    resource_id BIGINT NOT NULL REFERENCES resources(id) ON DELETE RESTRICT,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    CONSTRAINT chk_schedule_range CHECK (end_time > start_time),
    CONSTRAINT ex_schedule_no_overlap EXCLUDE USING gist (
        resource_id WITH =,
        day_of_week WITH =,
        timerange(start_time, end_time) WITH &&
    )
);

-- Bloqueos puntuales (mantenimiento, festivos) que anulan la disponibilidad
CREATE TABLE IF NOT EXISTS resource_blocks (
    id          BIGSERIAL PRIMARY KEY,
    resource_id BIGINT NOT NULL REFERENCES resources(id) ON DELETE RESTRICT,
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ NOT NULL,
    reason      VARCHAR(255),
    created_by  BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_block_range CHECK (ends_at > starts_at)
);

CREATE TABLE IF NOT EXISTS reservations (
    id                  BIGSERIAL PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    service_id          BIGINT NOT NULL,
    resource_id         BIGINT NOT NULL,
    starts_at           TIMESTAMPTZ NOT NULL,
    ends_at             TIMESTAMPTZ NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA',
    cancelled_at        TIMESTAMPTZ,
    cancelled_by        BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    cancellation_reason VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_res_service_resource FOREIGN KEY (service_id, resource_id)
        REFERENCES service_resources(service_id, resource_id) ON DELETE RESTRICT,
    CONSTRAINT chk_res_status CHECK (status IN ('CONFIRMADA', 'CANCELADA', 'COMPLETADA')),
    CONSTRAINT chk_res_range  CHECK (ends_at > starts_at),
    CONSTRAINT chk_res_cancel CHECK (
        (status = 'CANCELADA') = (cancelled_at IS NOT NULL)
        AND (cancelled_at IS NOT NULL OR (cancelled_by IS NULL AND cancellation_reason IS NULL))
    ),
    -- Sin doble reserva del mismo recurso en horarios que se traslapan
    CONSTRAINT ex_res_no_overlap EXCLUDE USING gist (
        resource_id WITH =,
        tstzrange(starts_at, ends_at) WITH &&
    ) WHERE (status <> 'CANCELADA')
);

-- ------------------------------------------------------------
-- 3) Índices (los EXCLUDE ya crean índices gist para resource_schedules y reservations)
-- ------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uq_services_business_name
    ON services (business_id, lower(name)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_resources_business_name
    ON resources (business_id, lower(name)) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_services_business_active
    ON services (business_id) WHERE deleted_at IS NULL AND is_active;
CREATE INDEX IF NOT EXISTS idx_resources_business_active
    ON resources (business_id) WHERE deleted_at IS NULL AND is_active;
CREATE INDEX IF NOT EXISTS idx_service_resources_resource
    ON service_resources (resource_id);
CREATE INDEX IF NOT EXISTS idx_resource_blocks_overlap
    ON resource_blocks USING gist (resource_id, tstzrange(starts_at, ends_at));
CREATE INDEX IF NOT EXISTS idx_reservations_client
    ON reservations (client_id, starts_at DESC);
CREATE INDEX IF NOT EXISTS idx_reservations_service
    ON reservations (service_id, starts_at);

-- ------------------------------------------------------------
-- 4) Reglas de negocio en BD (triggers)
-- ------------------------------------------------------------

-- 4.1 updated_at automático
CREATE OR REPLACE FUNCTION fn_set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_services_updated_at ON services;
CREATE TRIGGER trg_services_updated_at BEFORE UPDATE ON services
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
DROP TRIGGER IF EXISTS trg_resources_updated_at ON resources;
CREATE TRIGGER trg_resources_updated_at BEFORE UPDATE ON resources
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
DROP TRIGGER IF EXISTS trg_reservations_updated_at ON reservations;
CREATE TRIGGER trg_reservations_updated_at BEFORE UPDATE ON reservations
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- 4.2 Validar una reserva nueva o reprogramada
--     (futura, servicio/recurso activos, duración = la del servicio,
--      dentro del horario del recurso y sin bloqueos). Zona horaria: America/Bogota
CREATE OR REPLACE FUNCTION fn_validate_reservation() RETURNS TRIGGER AS $$
DECLARE
    v_tz       CONSTANT TEXT := 'America/Bogota';
    v_duration INT;
    v_local_s  TIMESTAMP;
    v_local_e  TIMESTAMP;
BEGIN
    IF NEW.status = 'CANCELADA' THEN
        RETURN NEW;
    END IF;

    IF TG_OP = 'INSERT' AND NEW.starts_at <= now() THEN
        RAISE EXCEPTION 'RESERVA_EN_EL_PASADO' USING ERRCODE = 'P0001';
    END IF;

    SELECT s.duration_minutes INTO v_duration
    FROM services s
    JOIN resources r ON r.id = NEW.resource_id
    WHERE s.id = NEW.service_id
      AND s.is_active AND s.deleted_at IS NULL
      AND r.is_active AND r.deleted_at IS NULL;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'SERVICIO_O_RECURSO_NO_DISPONIBLE' USING ERRCODE = 'P0001';
    END IF;

    IF NEW.ends_at - NEW.starts_at <> v_duration * INTERVAL '1 minute' THEN
        RAISE EXCEPTION 'DURACION_NO_COINCIDE_CON_SERVICIO' USING ERRCODE = 'P0001';
    END IF;

    v_local_s := NEW.starts_at AT TIME ZONE v_tz;
    v_local_e := NEW.ends_at   AT TIME ZONE v_tz;
    IF v_local_s::date <> v_local_e::date
       OR NOT EXISTS (
            SELECT 1 FROM resource_schedules rs
            WHERE rs.resource_id = NEW.resource_id
              AND rs.day_of_week = EXTRACT(ISODOW FROM v_local_s)
              AND rs.start_time <= v_local_s::time
              AND rs.end_time   >= v_local_e::time
       ) THEN
        RAISE EXCEPTION 'RESERVA_FUERA_DE_HORARIO' USING ERRCODE = 'P0001';
    END IF;

    IF EXISTS (
        SELECT 1 FROM resource_blocks b
        WHERE b.resource_id = NEW.resource_id
          AND tstzrange(b.starts_at, b.ends_at) && tstzrange(NEW.starts_at, NEW.ends_at)
    ) THEN
        RAISE EXCEPTION 'RECURSO_BLOQUEADO' USING ERRCODE = 'P0001';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_validate_reservation ON reservations;
CREATE TRIGGER trg_validate_reservation
    BEFORE INSERT OR UPDATE OF starts_at, ends_at, resource_id, service_id ON reservations
    FOR EACH ROW EXECUTE FUNCTION fn_validate_reservation();

-- 4.3 Una reserva cancelada no vuelve a activarse
CREATE OR REPLACE FUNCTION fn_reservation_status_guard() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = 'CANCELADA' AND NEW.status <> 'CANCELADA' THEN
        RAISE EXCEPTION 'RESERVA_CANCELADA_NO_REACTIVABLE' USING ERRCODE = 'P0001';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_reservation_status_guard ON reservations;
CREATE TRIGGER trg_reservation_status_guard
    BEFORE UPDATE OF status ON reservations
    FOR EACH ROW EXECUTE FUNCTION fn_reservation_status_guard();

-- 4.4 No eliminar/desactivar servicios o recursos con reservas futuras confirmadas
CREATE OR REPLACE FUNCTION fn_block_deactivate_service() RETURNS TRIGGER AS $$
BEGIN
    IF (OLD.is_active AND OLD.deleted_at IS NULL)
       AND (NOT NEW.is_active OR NEW.deleted_at IS NOT NULL)
       AND EXISTS (SELECT 1 FROM reservations
                   WHERE service_id = NEW.id AND status = 'CONFIRMADA' AND starts_at > now()) THEN
        RAISE EXCEPTION 'SERVICIO_CON_RESERVAS_FUTURAS' USING ERRCODE = 'P0001';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_block_deactivate_service ON services;
CREATE TRIGGER trg_block_deactivate_service
    BEFORE UPDATE OF is_active, deleted_at ON services
    FOR EACH ROW EXECUTE FUNCTION fn_block_deactivate_service();

CREATE OR REPLACE FUNCTION fn_block_deactivate_resource() RETURNS TRIGGER AS $$
BEGIN
    IF (OLD.is_active AND OLD.deleted_at IS NULL)
       AND (NOT NEW.is_active OR NEW.deleted_at IS NOT NULL)
       AND EXISTS (SELECT 1 FROM reservations
                   WHERE resource_id = NEW.id AND status = 'CONFIRMADA' AND starts_at > now()) THEN
        RAISE EXCEPTION 'RECURSO_CON_RESERVAS_FUTURAS' USING ERRCODE = 'P0001';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_block_deactivate_resource ON resources;
CREATE TRIGGER trg_block_deactivate_resource
    BEFORE UPDATE OF is_active, deleted_at ON resources
    FOR EACH ROW EXECUTE FUNCTION fn_block_deactivate_resource();

-- ------------------------------------------------------------
-- 5) Privilegios mínimos para el usuario de aplicación (si existe)
-- ------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        GRANT SELECT, INSERT, UPDATE ON services, resources, reservations TO app_user;
        GRANT SELECT, INSERT, UPDATE, DELETE ON service_resources, resource_schedules, resource_blocks TO app_user;
        GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user;
    END IF;
END $$;

-- ===================== SEGURIDAD =====================
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
