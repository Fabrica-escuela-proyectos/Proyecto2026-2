-- ============================================================
-- PROPUESTA de BD (BD-03) — NO aplicada. Para revisión de Arquisoft; si se aprueba, el responsable de las
-- migraciones la copia a reservas-backend/src/main/resources/db/migration/ con el siguiente número libre.
-- Probada en PostgreSQL 16 local sobre V1–V12: aplica sin errores, las pruebas de docs/bd/pruebas siguen
-- en verde y no requiere cambios de código (ddl-auto: validate no se ve afectado: no cambia columnas).
-- Responsable BD: Juan Sebastián Andraus López. Detalle: docs/bd/modelo/documentacion_bd_sprint2.md §5.
-- ============================================================

-- 1) Índice para el ON DELETE SET NULL de bookings.service_id.
--    Hoy bookings no tiene índice que empiece por service_id: al borrar un servicio (o un negocio/proveedor
--    completo, HU-28) PostgreSQL recorre toda la tabla de reservas por cada servicio borrado.
--    Medido con 200.000 reservas: borrar un proveedor con 5 servicios pasó de ≈ 410 ms a ≈ 220 ms.
CREATE INDEX idx_bookings_service_id ON bookings (service_id);

-- 2) Hallazgo H-01: borrar un proveedor en la MISMA transacción en que se creó uno de sus servicios o
--    recursos falla (23503 sobre services_business_id_fkey), porque la cascada negocio→servicio y el
--    SET NULL de created_by alcanzan la misma fila. Con datos de transacciones anteriores (el caso real de
--    HU-28) no ocurre, pero el resultado depende de un detalle interno de PostgreSQL. Diferir la
--    verificación de la FK al COMMIT lo elimina; la integridad se sigue garantizando (un business_id
--    inexistente se rechaza igual, solo que al confirmar la transacción).
ALTER TABLE services  ALTER CONSTRAINT services_business_id_fkey  DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE resources ALTER CONSTRAINT resources_business_id_fkey DEFERRABLE INITIALLY DEFERRED;
