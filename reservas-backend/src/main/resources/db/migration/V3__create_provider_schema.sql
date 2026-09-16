-- Esquema del modulo Provider (HU-03 - Registro de proveedor de servicios).
--
-- Sigue el mismo estilo que V1/V2 (UUID + gen_random_uuid, TIMESTAMPTZ) en
-- vez del BIGSERIAL del modelo fisico "formal" de Andraus
-- (docs/bd/modelo/03_modelo_fisico.md), por la misma razon documentada en
-- V1: ese modelo formal y el esquema que realmente aplica Flyway siguen sin
-- conciliarse (ver docs/estado-proyecto-sprint-1.md, seccion "Riesgos").
-- Los nombres de tabla/columna (providers, businesses, user_id, provider_id,
-- name) si coinciden con el modelo logico/fisico documentado.
--
-- user_id / provider_id se guardan como columna UUID simple, no como FK con
-- @ManyToOne en el lado Java: provider.domain.Provider no importa
-- identity.domain.User (ADR-003-modularidad-e-interfaces.md). La integridad
-- referencial se mantiene igual a nivel de base de datos con REFERENCES.

CREATE TABLE providers (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_providers_user_id UNIQUE (user_id)
);

CREATE TABLE businesses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    name        VARCHAR(150) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Consulta clave de HU-06: listar los negocios de un proveedor para
-- verificar pertenencia (ver ProviderQueryService).
CREATE INDEX idx_businesses_provider_id ON businesses (provider_id);
