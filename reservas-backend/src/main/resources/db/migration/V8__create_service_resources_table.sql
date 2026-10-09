-- HU-18 - Asignar recursos a un servicio (tabla puente servicio <-> recurso).
--
-- PK compuesta (un recurso solo puede asignarse una vez al mismo servicio) y
-- ON DELETE CASCADE hacia ambos lados: si se borra el servicio o el recurso,
-- la asignacion desaparece (docs/bd/convenciones-bd.md). Que servicio y
-- recurso pertenezcan al MISMO negocio lo garantiza la aplicacion
-- (ServiceResourceAssignmentServiceImpl), no esta tabla.

CREATE TABLE service_resources (
    service_id  UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    resource_id UUID NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (service_id, resource_id)
);

-- Consultas inversas: en que servicios se usa un recurso (HU-16, HU-20, HU-22).
CREATE INDEX idx_service_resources_resource_id ON service_resources (resource_id);
