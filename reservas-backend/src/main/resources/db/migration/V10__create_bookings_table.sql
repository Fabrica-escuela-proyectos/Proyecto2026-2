-- HU-22 - Crear reserva (base de HU-23..28 y de la disponibilidad de HU-20).
--
-- ANTI-OVERBOOKING EN LA BASE: la restriccion EXCLUDE impide que dos reservas
-- CONFIRMADAS de un mismo recurso se traslapen en el tiempo, aun si dos
-- peticiones llegan a la vez. Requiere la extension btree_gist (incluida en
-- PostgreSQL y "confiable" desde la 13: la puede crear el dueño de la base sin
-- ser superusuario; en Render hay que verificarlo al desplegar).
-- Una violacion llega como SQLSTATE 23P01 y la API la traduce a 409.
--
-- SNAPSHOTS (client_email, service_name, business_name, resource_name,
-- price_cop): la reserva conserva lo que el cliente vio aunque despues se
-- edite o elimine el servicio, el recurso o el negocio (referencias con
-- ON DELETE SET NULL, nunca RESTRICT: docs/bd/convenciones-bd.md).
-- start_at / end_at son instantes (TIMESTAMPTZ); la aplicacion los calcula
-- desde la hora local de Bogota. Estados: CONFIRMADA (inicial), CANCELADA, COMPLETADA.

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE bookings (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id     UUID REFERENCES users(id) ON DELETE SET NULL,
    client_email  VARCHAR(150) NOT NULL,
    business_id   UUID REFERENCES businesses(id) ON DELETE SET NULL,
    business_name VARCHAR(150) NOT NULL,
    service_id    UUID REFERENCES services(id) ON DELETE SET NULL,
    service_name  VARCHAR(150) NOT NULL,
    resource_id   UUID REFERENCES resources(id) ON DELETE SET NULL,
    resource_name VARCHAR(150) NOT NULL,
    start_at      TIMESTAMPTZ NOT NULL,
    end_at        TIMESTAMPTZ NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA',
    price_cop     BIGINT NOT NULL,
    cancel_reason VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at  TIMESTAMPTZ,
    CONSTRAINT ck_bookings_range CHECK (end_at > start_at),
    CONSTRAINT ck_bookings_status CHECK (status IN ('CONFIRMADA', 'CANCELADA', 'COMPLETADA')),
    CONSTRAINT ck_bookings_price CHECK (price_cop >= 0),
    CONSTRAINT ex_bookings_no_overlap EXCLUDE USING gist (
        resource_id WITH =,
        tstzrange(start_at, end_at) WITH &&
    ) WHERE (status = 'CONFIRMADA' AND resource_id IS NOT NULL)
);

-- HU-23 (reservas del cliente), HU-24 (reservas del negocio) y HU-20 (ocupacion por recurso y dia).
CREATE INDEX idx_bookings_client_id ON bookings (client_id, start_at);
CREATE INDEX idx_bookings_business_id ON bookings (business_id, start_at);
CREATE INDEX idx_bookings_resource_start ON bookings (resource_id, start_at);
