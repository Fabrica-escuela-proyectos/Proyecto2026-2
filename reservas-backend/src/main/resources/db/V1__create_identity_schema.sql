-- Migracion inicial del modulo Identity & Access (HU-01 en adelante).
--
-- Fuente: MatrizProvisionalHU.docx (fila HU01, tablas: users, roles,
-- user_roles, audit_logs) + HU-01-Registrar-cliente.txt (unicidad de email
-- y celular, contrasena nunca en texto plano) + arquitectura-sprint-1.md
-- seccion 4.4 (persistencia: PostgreSQL, migraciones versionadas, claves
-- primarias/foraneas, restricciones de integridad).
--
-- La columna phone_number se mantiene con ese nombre aunque el DTO y la
-- entidad Java ahora usan "cellphone" (dtos-sprint-1.md); no hay necesidad
-- de que el nombre de columna fisico coincida con el nombre de campo Java.
--
-- IMPORTANTE: este script sigue siendo una PROPUESTA desde el backend,
-- consultas.md y modelo.md (entregables formales de BD) estaban vacios al
-- momento de este refactor. El modelo formal (conceptual, logico y fisico)
-- sigue siendo responsabilidad de Andraus en Bases de Datos y debe
-- validarse contra este script antes de darlo por definitivo (ver
-- docs/matriz-actualizaciones.md para los puntos abiertos).

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE roles (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name     VARCHAR(150) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    phone_number  VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone_number)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);

-- Estructura minima de auditoria (Lineamientos 5.3: "registrar auditoria
-- para acciones criticas sin almacenar secretos ni datos personales
-- innecesarios en logs").
CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type    VARCHAR(40) NOT NULL,
    subject_email VARCHAR(150),
    outcome       VARCHAR(20) NOT NULL,
    detail        VARCHAR(300),
    origin_ip     VARCHAR(45),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indices para las consultas clave de HU-01 (verificar unicidad) y de HU02
-- (login por email), segun lo identificado en la Matriz tecnica.
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_audit_logs_event_type ON audit_logs (event_type);

-- Seed minimo de roles requerido para que HU-01 pueda asignar "Cliente"
-- por defecto. PROVEEDOR y ADMINISTRADOR se dejan creados porque HU03 y
-- HU05 (fuera del alcance de Simon en Sprint 1) dependen de este catalogo.
INSERT INTO roles (name) VALUES ('CLIENTE'), ('PROVEEDOR'), ('ADMINISTRADOR');
