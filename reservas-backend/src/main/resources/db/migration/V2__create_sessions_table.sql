-- Tabla de control de sesiones, requerida por
-- ADR-002-autenticacion-y-sesiones.md sección 5 ("Revocación de
-- sesiones"): aunque la autenticación usa JWT, se necesita poder revocar
-- una sesión antes de que el token expire por sí solo.
--
-- IMPORTANTE: esta tabla es preparación arquitectónica para HU02 (Inicio
-- de sesión) y HU04 (Cerrar sesión), que no son tareas individuales de
-- Simon en Sprint 1. Ningún flujo la usa todavía; ver
-- identity.domain.Session y identity.infrastructure.SessionRepository.
--
-- No se almacena el JWT completo (ADR-002 sección 2), solo el
-- identificador del token (claim "jti") necesario para poder revocarlo.

CREATE TABLE sessions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_id   VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_sessions_token_id UNIQUE (token_id)
);

-- Consulta clave anticipada para HU02/HU04/HU06: validar si una sesion
-- sigue vigente a partir de su token_id.
CREATE INDEX idx_sessions_token_id ON sessions (token_id);
CREATE INDEX idx_sessions_user_id ON sessions (user_id);
