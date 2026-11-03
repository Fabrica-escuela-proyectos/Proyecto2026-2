-- HU-07 - Editar información del negocio (Sprint 3).
--
-- El negocio solo tenía nombre (HU-03). La HU pide descripción, dirección, tipo de
-- negocio y teléfono, todos opcionales salvo el nombre. Los tipos son una lista
-- cerrada (el AC rechaza "Panadería" y "123"); el teléfono son 10 dígitos de un
-- celular (3...) o de un fijo nacional (60...). Supuestos D7 y D8 del plan del
-- Sprint 3 (docs/sprint-3/plan-de-trabajo-sprint-3.md), a confirmar con Calidad/PO.
--
-- Nota de numeración: la propuesta de BD docs/bd/propuestas/V13__... no se aplicó;
-- si se aprueba, pasa al siguiente número libre.

ALTER TABLE businesses
    ADD COLUMN description   VARCHAR(500),
    ADD COLUMN address       VARCHAR(200),
    ADD COLUMN business_type VARCHAR(30),
    ADD COLUMN phone         VARCHAR(10),
    ADD COLUMN updated_at    TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE businesses
    ADD CONSTRAINT ck_businesses_type CHECK (business_type IN
        ('SALUD', 'BELLEZA', 'BIENESTAR', 'DEPORTE', 'EDUCACION', 'CONSULTORIA', 'ENTRETENIMIENTO', 'OTRO')),
    ADD CONSTRAINT ck_businesses_phone CHECK (phone ~ '^(3[0-9]{9}|60[0-9]{8})$');
