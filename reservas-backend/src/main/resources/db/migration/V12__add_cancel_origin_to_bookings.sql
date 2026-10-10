-- HU-25 (y base de HU-26 / HU-28) - Quien o que cancelo la reserva.
--
-- Regla acordada en el plan (seccion 6): una cancelacion es estado CANCELADA + un origen,
-- en vez de estados compuestos ("Cancelada por proveedor"). El motivo en texto libre sigue
-- en cancel_reason (lo escribe quien cancela). Las reservas canceladas antes de esta
-- migracion quedan con origen NULL (no se sabe quien las cancelo).

ALTER TABLE bookings ADD COLUMN cancel_origin VARCHAR(30);

ALTER TABLE bookings ADD CONSTRAINT ck_bookings_cancel_origin CHECK (cancel_origin IN
    ('CLIENTE', 'PROVEEDOR', 'ELIMINACION_CUENTA', 'RECURSO_NO_DISPONIBLE', 'SERVICIO_NO_DISPONIBLE'));
