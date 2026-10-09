-- Esquema del modulo Service (HU-09 - Crear servicio; base de HU-10..13, 18, 20, 22).
--
-- Convenciones (docs/bd/convenciones-bd.md, propuesta BD-01): UUID con
-- gen_random_uuid, TIMESTAMPTZ, dinero en pesos enteros (BIGINT, COP no usa
-- decimales), ON DELETE CASCADE hacia el dueño del dato (negocio) y SET NULL
-- hacia users (nunca RESTRICT: no debe impedir eliminar usuarios, HU-05/HU-28),
-- constraints con prefijo uk_/ck_ e indices con prefijo idx_.
--
-- business_id / created_by son UUID simples en Java (sin @ManyToOne): el modulo
-- service no importa las entidades de provider ni identity (ADR-003); la
-- integridad referencial se mantiene aqui con REFERENCES.

CREATE TABLE services (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id      UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name             VARCHAR(150) NOT NULL,
    description      VARCHAR(500),
    duration_minutes INT NOT NULL,
    price_cop        BIGINT NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_by       UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_services_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_services_price CHECK (price_cop >= 0)
);

-- Nombre unico por negocio sin distinguir mayusculas (regla de HU-09). Tambien
-- es la defensa real contra dos creaciones simultaneas con el mismo nombre.
CREATE UNIQUE INDEX uk_services_business_name ON services (business_id, lower(name));

-- Listados por negocio (HU-09, HU-13).
CREATE INDEX idx_services_business_id ON services (business_id);
