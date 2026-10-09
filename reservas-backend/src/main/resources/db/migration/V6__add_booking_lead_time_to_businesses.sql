-- HU-08 - Antelacion minima de reserva por negocio.
--
-- Horas enteras entre 1 y 720 (30 dias; supuesto del equipo, a confirmar con
-- QA/PO). Valor por defecto: 1 hora. HU-22 (crear reserva) exige este valor.
-- Convenciones: docs/bd/convenciones-bd.md.

ALTER TABLE businesses
    ADD COLUMN min_advance_hours INT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_businesses_min_advance_hours CHECK (min_advance_hours BETWEEN 1 AND 720);
