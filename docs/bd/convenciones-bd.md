# Convenciones de base de datos (BD-01) — propuesta

**Estado: propuesta para acordar con el equipo de BD (Andraus) y Arquisoft.** Recoge lo que ya aplican las migraciones Flyway reales (V1–V5) y fija las reglas para las tablas nuevas del Sprint 2, para que el modelo formal ([docs/bd/modelo](modelo/)) y el esquema real dejen de divergir (ver [conciliación](../conciliacion-modelo-bd-sprint-1.md)). Mientras no haya acuerdo, **manda el esquema de Flyway** (`reservas-backend/src/main/resources/db/migration/`).

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

**Decisiones abiertas para BD:** (1) tope de longitudes comunes (`name` 150, `description` 500); (2) si `bookings` usa una restricción de exclusión (`EXCLUDE USING gist` sobre rango de tiempo) para el anti-overbooking de HU-22; (3) si el modelo formal pasa a UUID o se mantiene `BIGSERIAL` solo como documentación.
