-- Respuesta de Calidad del 2026-11-06 a la pregunta P12 (supuesto S-27):
-- «Dejarlo en Cliente eliminado: no se muestran datos de clientes ya eliminados».
--
-- Antes, una reserva conservaba el nombre y el correo del cliente aunque su cuenta
-- se eliminara (ADR-006). Desde ahora, al eliminar la cuenta (HU-05/HU-28) la
-- aplicación reemplaza el nombre por "Cliente eliminado" y borra el correo; el resto
-- del historial (servicio, recurso, horario, precio, estado) se conserva.
--
-- client_email deja de ser obligatorio y se limpian las reservas de clientes ya
-- eliminados: client_id solo queda en NULL cuando se borra el usuario (ON DELETE SET NULL).

ALTER TABLE bookings ALTER COLUMN client_email DROP NOT NULL;

UPDATE bookings
SET client_name = 'Cliente eliminado', client_email = NULL
WHERE client_id IS NULL;
