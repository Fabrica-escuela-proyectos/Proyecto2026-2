-- Tabla de configuracion de MFA (TOTP), requerida por
-- ADR-002-autenticacion-y-sesiones.md seccion 8 y usada por HU-02
-- (verificacion adicional para cuentas administrativas) y HU-05 (evento
-- obligatorio de creacion de MFA al ascender a un usuario a
-- ADMINISTRADOR). Ver identity.domain.Mfa y identity.application.MfaService.
--
-- El secreto se guarda en texto (Base32) y no con hash, a diferencia de
-- password_hash: a diferencia de una contrasena, el secreto TOTP se debe
-- poder leer de vuelta para calcular el codigo esperado en cada intento de
-- verificacion. Nunca se expone salvo una vez, en la respuesta de
-- POST /api/v1/auth/mfa/setup, mientras enabled=false.

CREATE TABLE mfa (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    secret     VARCHAR(64) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_mfa_user_id UNIQUE (user_id)
);
