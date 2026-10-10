-- HU-24 - El proveedor ve el NOMBRE del cliente en las reservas de su negocio.
--
-- Igual que client_email, es una copia ("snapshot") del nombre vigente al reservar:
-- sobrevive a que el cliente cambie su nombre o elimine su cuenta (client_id pasa a
-- NULL, HU-05/HU-28) y evita unir con users en cada listado.
-- Las filas anteriores a esta migracion se completan con el nombre actual del usuario
-- y, si el usuario ya no existe, con el correo guardado.

ALTER TABLE bookings ADD COLUMN client_name VARCHAR(150);

UPDATE bookings b SET client_name = u.full_name FROM users u WHERE b.client_id = u.id;
UPDATE bookings SET client_name = client_email WHERE client_name IS NULL;

ALTER TABLE bookings ALTER COLUMN client_name SET NOT NULL;
