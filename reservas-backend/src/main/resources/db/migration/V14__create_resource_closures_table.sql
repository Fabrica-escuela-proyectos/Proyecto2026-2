-- HU-21 - Bloquear un horario puntual de un recurso (Sprint 3).
--
-- Un cierre deja al recurso sin atención en un periodo concreto (mantenimiento,
-- festivo) sin tocar su horario semanal (resource_availability, HU-19). Puede ser
-- un día completo o un rango con fecha y hora; start_at/end_at son instantes
-- (TIMESTAMPTZ) calculados desde la hora de Bogotá.
--
-- Igual que en bookings (ADR-005), la base impide dos cierres traslapados del
-- mismo recurso aunque lleguen dos peticiones a la vez (EXCLUDE con btree_gist,
-- extensión que ya crea V10). Un borde compartido no es traslape ([a,b) y [b,c)).
-- Al borrar el recurso se borran sus cierres (no son historial de nadie).

CREATE TABLE resource_closures (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id UUID NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    start_at    TIMESTAMPTZ NOT NULL,
    end_at      TIMESTAMPTZ NOT NULL,
    reason      VARCHAR(300),
    created_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_resource_closures_range CHECK (end_at > start_at),
    CONSTRAINT ex_resource_closures_no_overlap EXCLUDE USING gist (
        resource_id WITH =,
        tstzrange(start_at, end_at) WITH &&
    )
);

CREATE INDEX idx_resource_closures_resource_start ON resource_closures (resource_id, start_at);
