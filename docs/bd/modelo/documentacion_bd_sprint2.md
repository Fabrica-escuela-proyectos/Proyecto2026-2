# Documentación de BD — Sprint 2

Responsable: Juan Sebastián Andraus López (BD principal) · Actualizado: 2026-10-12 · Esquema de referencia: Flyway `V1`–`V12`.

## 1. Entregables por tarea de Azure

| Tarea | Entregable | Archivo | Estado |
|---|---|---|---|
| BD-01 Convenciones y alineación | Convenciones acordadas; modelo formal describe el esquema real | `docs/bd/convenciones-bd.md` · `modelo/modelo_sprint2.md` | Hecho |
| BD-02 MER y entidades | MER gráfico y entidades del Sprint 2 | `modelo/ER_sprint2.png/.svg/.dot` · `modelo_sprint2.md` §1 | Hecho |
| BD-03 Modelo físico y DDL → V5+ | Modelo físico de V5–V12 + propuesta V13 | `modelo_sprint2.md` §2–§4 · `propuestas/V13__…sql` | Hecho (V13 en revisión) |
| BD-04 Anti-overbooking + índices + concurrencia | EXCLUDE verificado; índices revisados con EXPLAIN | `pruebas/pruebas_bd_sprint2.sql` (T01–T09) · §4 | Hecho (concurrencia real: pruebas del backend) |
| BD-05 Consultas clave con EXPLAIN | 13 consultas de disponibilidad, reservas y cancelaciones masivas | `consultas/consultas_sprint2.sql` | Hecho |
| BD-06 Scripts finales y volumen | Datos de ejemplo, integridad, volumen 200k, verificación | `pruebas/*.sql` | Hecho |
| BD-07 Seguridad en BD | Rol de ejecución con privilegios mínimos | `scripts/seguridad_bd_sprint2.sql` · `pruebas/pruebas_privilegios_sprint2.sql` | Propuesta (falta aplicar en Render) |

## 2. Cómo ejecutar (BD local o de pruebas; nunca contra Render salvo la verificación)

1. Esquema: arrancar el backend (Flyway aplica V1–V12) o aplicar las migraciones en orden con `psql`.
2. `pruebas/verificacion_esquema_sprint2.sql` → 18 filas `OK` (solo lectura; sirve también en Render).
3. `pruebas/datos_semilla_sprint2.sql` → datos de ejemplo con UUID fijos (re-ejecutable; contraseñas no válidas).
4. `consultas/consultas_sprint2.sql` → las escrituras van entre `BEGIN … ROLLBACK`.
5. `pruebas/pruebas_bd_sprint2.sql` → `45 OK, 0 FALLO` (re-ejecutable, limpia sus datos; sirve en psql o en un editor web).
6. `pruebas/pruebas_volumen_sprint2.sql` (psql) → 200.000 reservas con `ROLLBACK`.
7. Seguridad: `scripts/seguridad_bd_sprint2.sql` como dueño y luego `pruebas/pruebas_privilegios_sprint2.sql` (psql con superusuario).

## 3. Qué se probó y qué no

**Probado en PostgreSQL 16.15 local (2026-10-12):**
- V1–V12 del repo aplicadas en orden sobre BD vacía, como superusuario y como usuario dueño **no** superusuario (igual que Render: `btree_gist` se crea sin superusuario).
- Integridad: **45/45 OK**, dos ejecuciones seguidas, sesión en UTC y en America/Bogota, con y sin la propuesta V13.
- Consultas C01–C13 con los datos de ejemplo, en UTC.
- Privilegios del rol propuesto: **13/13 OK** (puede operar y eliminar cuentas; no puede borrar historial, modificar auditoría, TRUNCATE, DROP, ALTER ni crear tablas).
- Verificación de esquema: 18/18 OK.
- Volumen: tabla de §4.

**No probado:**
- Contra Render ni contra la suite del backend (sin Docker en esta sesión). La propuesta V13 y el cambio de usuario de BD (BD-07) deben pasar `./mvnw -B clean test` antes de aplicarse.
- Concurrencia con sesiones simultáneas: la cubren `BookingCreationIntegrationTest` y `ResourceLifecycleIntegrationTest` del backend (ADR-005 §5).

## 4. Volumen (PostgreSQL 16 local, 200.000 reservas)

100 negocios, 500 recursos, 20.100 usuarios; 40 % completadas, 40 % confirmadas, 20 % canceladas. Carga ≈ 9–12 s.

| Consulta | Tiempo | Plan |
|---|---|---|
| C03 HU-20 disponibilidad (SQL completo) | 0,7 ms | GiST `ex_bookings_no_overlap` |
| C04 HU-20 reservas de los recursos en un día (la que usa el backend) | 0,6 ms | GiST `ex_bookings_no_overlap` |
| C05 HU-22 ¿recurso ocupado? | 0,1 ms | GiST |
| C07 HU-23 mis reservas (página) | 0,06 ms | `idx_bookings_client_id` hacia atrás |
| C08 HU-24 reservas del negocio (página) | 0,04 ms | `idx_bookings_business_id` hacia atrás |
| C10 HU-16 contar / cancelar futuras de un recurso | 0,2 / 6,5 ms | GiST |
| C11 HU-28 cancelar futuras de un negocio (≈ 800 filas) | 26 ms | `idx_bookings_business_id` |
| C11b HU-28 eliminar proveedor (cascada + SET NULL de 2.000 reservas) | 310–410 ms | **seq scan** por `bookings.service_id` (sin índice) |
| C13 reporte mensual de cancelación | 38 ms | Seq Scan paralelo (reporte, no ruta crítica) |
| Doble reserva con 200k filas | rechazada | `ex_bookings_no_overlap` |

Observación: escribir el traslape como `tstzrange(...) && tstzrange(...)` (igual que el EXCLUDE) permite usar el índice GiST; con `start_at < :fin AND end_at > :inicio` dentro de un `NOT EXISTS` el planificador eligió un recorrido completo (43 ms vs 0,7 ms en C03). La consulta del backend (C04) ya usa el índice.

## 5. Hallazgos y propuesta V13 (`propuestas/V13__bd_indice_reservas_servicio_y_fk_diferidas.sql`)

| # | Hallazgo | Impacto | Propuesta |
|---|---|---|---|
| H-01 | Si un proveedor se elimina **en la misma transacción** en que se creó uno de sus servicios/recursos, el `DELETE` falla (23503 en `services_business_id_fkey`): la cascada negocio→servicio y el `SET NULL` de `created_by` tocan la misma fila. Con datos de transacciones anteriores (caso real de HU-28) funciona | Bajo: no ocurre por la API; sí en scripts o pruebas que lo hagan todo en una transacción | FK `business_id` de `services` y `resources` `DEFERRABLE INITIALLY DEFERRED` (probado: elimina el error y mantiene la integridad al confirmar) |
| H-02 | `bookings.service_id` no tiene índice; el `SET NULL` al borrar servicios recorre toda la tabla | Medio a futuro: eliminar un proveedor ≈ 0,3–0,4 s con 200k reservas | `idx_bookings_service_id` (≈ −45 %) |
| H-03 | El anti-replay de MFA (ADR-004 P8, MFA-06) necesita guardar el último paso TOTP usado | Pendiente de código | Columna `mfa.last_used_step BIGINT` + `UPDATE … WHERE last_used_step < :paso` (1 fila = válido). No va en V13 porque requiere la entidad Java |
| H-04 | Solo la app impide horarios traslapados y reservas fuera de horario | Aceptado (convenciones: bloqueo de fila) | Opcional: `EXCLUDE` también en `resource_availability` como defensa en profundidad |

## 6. Trazabilidad HU → tabla → consulta → prueba

| HU | Tablas / objetos | Consulta | Pruebas BD |
|---|---|---|---|
| HU-08 antelación | `businesses.min_advance_hours` (V6) | C03 | T15–T17 |
| HU-09 crear servicio | `services` (V5) | C01 | T18–T21 |
| HU-13 catálogo | `services`, `service_resources`, `resources` | C01 | — (lectura) |
| HU-14 registrar recurso | `resources` (V7) | — | T22, T23 |
| HU-16 / 17 desactivar / reactivar | `resources.active`, `bookings` | C10 | T29a–b |
| HU-18 asignar recursos | `service_resources` (V8) | C12 | T24, T28 |
| HU-19 horarios | `resource_availability` (V9) | C02 | T25–T27 |
| HU-20 disponibilidad | `resource_availability`, `service_resources`, `bookings` | C03, C04 | T04–T08 |
| HU-22 crear reserva | `bookings` (V10), EXCLUDE | C05, C06 | T01–T03, T09–T11, T13 |
| HU-23 mis reservas | `bookings` (copias) | C07 | — (lectura) |
| HU-24 reservas del negocio | `bookings.client_name` (V11) | C08 | T14 |
| HU-25 / 26 cancelar | `bookings.cancel_origin` (V12), `cancel_reason` | C09 | T12 |
| HU-28 eliminar cuenta | CASCADE / SET NULL, copias | C11 | T30–T33, T37 |
| Seguridad (BD-07) | rol `reservas_runtime` | — | P1–P4 |
| Regresión Sprint 1 | `users`, `mfa` | — | T34–T36 |

## 7. Pendientes y preguntas

1. Arquisoft: revisar y aplicar V13 (siguiente número libre) y correr la suite completa.
2. BD-07: ¿Render permite crear un segundo usuario? Si sí, `DB_USERNAME=reservas_runtime` y Flyway con `spring.flyway.user/password` (variables `DB_MIGRATION_*`); si no, queda documentado como riesgo aceptado.
3. S-27 (ADR-006): anonimizar `client_name`/`client_email` tras eliminar una cuenta es un `UPDATE` sobre `bookings`; la BD no requiere cambios.
4. Decisión abierta de ADR-005: si Calidad lee «mismo servicio» literal, cambiar la clave del EXCLUDE a `service_id` (una migración).
