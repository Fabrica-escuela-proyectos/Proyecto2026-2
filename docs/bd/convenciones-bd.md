# Convenciones de base de datos (BD-01)

**Estado: acordadas por BD el 2026-10-12**, a partir de la propuesta de Arquisoft. Recogen lo que aplican las migraciones Flyway reales (V1–V12); el modelo formal del Sprint 2 ([docs/bd/modelo/modelo_sprint2.md](modelo/modelo_sprint2.md)) ya describe ese esquema (ver [conciliación](../conciliacion-modelo-bd-sprint-1.md) para el Sprint 1). Ante cualquier diferencia, **manda el esquema de Flyway** (`reservas-backend/src/main/resources/db/migration/`).

| Tema | Convención | Por qué |
|---|---|---|
| Claves primarias | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` | Ya es lo que usan V1–V5; no exponer ids secuenciales en la API |
| Fechas | `TIMESTAMPTZ`, en UTC en la aplicación (`Instant`) | Reservas y disponibilidad necesitan una zona clara |
| Dinero | Pesos enteros (`BIGINT`, COP) con `CHECK (>= 0)` | El peso colombiano no usa decimales; evita errores de redondeo |
| Borrado hacia el padre dueño | `ON DELETE CASCADE` (negocio → servicios, recursos, horarios) | Borrar un proveedor/negocio limpia lo suyo |
| Referencias a `users` | `ON DELETE SET NULL` (columna nullable, p. ej. `created_by`) o `CASCADE` si el dato es del usuario; **nunca `RESTRICT`** | `RESTRICT` impediría eliminar usuarios (HU-05) y la HU-28 exige cancelar sus reservas, no bloquear |
| Nombres | Tablas en plural y `snake_case`; PK `id`; FK `<tabla_singular>_id`; booleanos `active`; `created_at`/`updated_at` | Coherencia con `users`, `providers`, `businesses` |
| Restricciones | `uk_<tabla>_<campos>` (únicas), `ck_<tabla>_<regla>` (CHECK), `idx_<tabla>_<campo>` (índices) | El manejador de errores traduce `uk_*` a 409 con mensaje propio |
| Unicidad sin mayúsculas | Índice único sobre `lower(campo)` | Ej.: `uk_services_business_name` |
| Enteros | `INT` (no `SMALLINT`) y `BIGINT` para dinero | `ddl-auto: validate` falla al arrancar si una columna `SMALLINT` se mapea a `int`/`Integer` en Java (pasó con `day_of_week`) |
| Horas del día | `TIME` + `CHECK (start_time < end_time)`; rangos semiabiertos `[inicio, fin)` | Dos rangos que solo comparten el borde no se traslapan; hora local del negocio (America/Bogota) |
| Estado | Borrado lógico con `active BOOLEAN` para servicios y recursos | HU-11/12/16/17 desactivan y reactivan; las reservas históricas conservan su referencia |
| Concurrencia | La unicidad se garantiza **en la base** (índice único); lo que la base no expresa fácil (p. ej. rangos de un mismo día sin traslape en `resource_availability`) se valida en Java **con bloqueo de fila** (`PESSIMISTIC_WRITE`) del padre | Dos peticiones simultáneas deben dar 201 y 409, no dos filas |
| Módulos | Columna UUID simple + `REFERENCES` en SQL; sin `@ManyToOne` entre módulos | ADR-003: un módulo no importa las entidades de otro |

**Decisiones que estaban abiertas (resueltas por BD el 2026-10-12):**
1. Longitudes comunes: `name` 150 y `description` 500 (como en V5/V7); copias en `bookings` también 150.
2. Anti-overbooking: sí, `EXCLUDE USING gist` sobre `tstzrange(start_at, end_at)` por recurso (V10, ADR-005). Para que las consultas de traslape usen ese índice, escribirlas con `tstzrange(...) && tstzrange(...)` (ver `docs/bd/modelo/documentacion_bd_sprint2.md` §4).
3. El modelo formal pasa a UUID: `modelo_sprint2.md` documenta el esquema real; los documentos `BIGSERIAL` del Sprint 1 quedan como histórico.

**Agregados de BD:** toda FK que se use en un `ON DELETE SET NULL`/`CASCADE` sobre tablas grandes debe tener índice que empiece por esa columna (falta `bookings.service_id`, propuesta V13 en `docs/bd/propuestas/`). Las migraciones nuevas las propone BD en `docs/bd/propuestas/` y las mueve a `db/migration` el responsable de Flyway tras la revisión de Arquisoft.
