-- HU-19 - Horarios de atencion (disponibilidad semanal) de un recurso.
--
-- day_of_week sigue ISO-8601: 1 = lunes ... 7 = domingo. Un dia sin filas =
-- no disponible. Los rangos son semiabiertos [start_time, end_time): dos rangos
-- consecutivos (09:00-12:00 y 12:00-14:00) no se traslapan. Las horas son hora
-- local del negocio (America/Bogota); no se guarda zona horaria porque no hay
-- negocios en otras zonas (a confirmar con QA/PO).
--
-- Que los rangos de un mismo dia no se traslapen lo valida la aplicacion
-- (ResourceAvailabilityServiceImpl) con bloqueo de la fila del recurso; esta
-- tabla garantiza solo la forma de cada rango.

CREATE TABLE resource_availability (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id UUID NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    day_of_week INT NOT NULL,
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_resource_availability_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_resource_availability_range CHECK (start_time < end_time)
);

-- Consulta de HU-19 (GET) y de HU-20 (horarios libres de un recurso en un dia).
CREATE INDEX idx_resource_availability_resource_day ON resource_availability (resource_id, day_of_week);
