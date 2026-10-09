-- Esquema del modulo Resource (HU-14 - Registrar recurso; base de HU-15..19, 20, 22).
--
-- Convenciones: docs/bd/convenciones-bd.md (UUID, TIMESTAMPTZ, CASCADE hacia el
-- negocio dueño, CHECK con prefijo ck_, unicos con prefijo uk_). business_id es
-- un UUID simple en Java (sin @ManyToOne): el modulo resource no importa las
-- entidades de provider (ADR-003).
--
-- Tipos: SALA | EQUIPO | PERSONAL (lista fija; a confirmar con QA/PO frente a la
-- descripcion de la HU "consultorio, sala, cancha, puesto").

CREATE TABLE resources (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name        VARCHAR(150) NOT NULL,
    type        VARCHAR(20) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_resources_type CHECK (type IN ('SALA', 'EQUIPO', 'PERSONAL'))
);

-- Nombre unico por negocio sin distinguir mayusculas; tambien defiende contra
-- dos registros simultaneos con el mismo nombre.
CREATE UNIQUE INDEX uk_resources_business_name ON resources (business_id, lower(name));

CREATE INDEX idx_resources_business_id ON resources (business_id);
