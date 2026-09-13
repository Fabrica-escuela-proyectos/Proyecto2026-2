

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
CREATE ROLE app_user LOGIN PASSWORD 'CHANGE_ME';
GRANT SELECT, INSERT, UPDATE ON
    users, roles, user_roles, permissions, role_permissions,
    providers, businesses, sessions, mfa
    TO app_user;
GRANT SELECT, INSERT ON audit_logs TO app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user;
