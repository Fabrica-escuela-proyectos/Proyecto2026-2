# ADR-005 — Anti-overbooking y control de concurrencia

* **Estado:** Propuesto. Implementado y probado en el Sprint 2; pendiente de la revisión de Juan Esteban González y Santiago Rendón.
* **Fecha:** 2026-10-10
* **Responsable:** Simon Betancur.
* **Decisión:** la garantía de «un recurso no se reserva dos veces en el mismo horario» vive **en la base de datos** (restricción `EXCLUDE` sobre rangos de tiempo), reforzada por **bloqueos de fila** (`SELECT … FOR UPDATE`) en las operaciones que compiten, en lugar de depender de verificaciones en memoria de la aplicación.

## 1. Contexto

HU-22 pide «no se permite crear dos reservas para el mismo servicio en el mismo horario (anti-overbooking)». La verificación «¿está libre?» seguida de «guardar» no es atómica: dos clientes que reservan a la vez pueden pasar ambos la verificación. El problema no se limita a reservar: desactivar un recurso (HU-16) cuenta y cancela reservas mientras otro cliente puede estar reservándolo; dos cancelaciones, dos ediciones de horario o dos asignaciones de recursos del mismo objeto pueden pisarse.

El despliegue actual es una sola instancia, pero la solución no debe depender de ello (Render puede escalar y el pipeline reinicia el servicio en cada despliegue).

## 2. Decisión

| # | Decisión |
|---|---|
| D1 | **Restricción en la tabla `bookings`:** `EXCLUDE USING gist (resource_id WITH =, tstzrange(start_at, end_at) WITH &&) WHERE (status = 'CONFIRMADA' AND resource_id IS NOT NULL)` (migración V10, extensión `btree_gist`). Dos reservas confirmadas de un mismo recurso **no pueden traslaparse**, ni aunque lleguen en el mismo milisegundo; cancelar o completar una reserva libera el horario (el `WHERE` solo mira las confirmadas). La violación (`SQLSTATE 23P01`) se traduce a `409`. |
| D2 | **El límite es por recurso, no por servicio.** Un servicio con dos recursos admite dos reservas simultáneas (una por recurso). Es la lectura que hace posible el modelo «servicio ↔ recursos» de HU-18. |
| D3 | **Doble defensa:** antes de guardar se consulta si el recurso está libre (mensaje claro: «ya tiene una reserva» o «fuera del horario»), y la restricción cubre la carrera. Sin esa consulta, el usuario recibiría siempre el mensaje genérico de la restricción. |
| D4 | **Bloqueo pesimista de fila del objeto que compite** (`PESSIMISTIC_WRITE`): la reserva (cancelar, HU-25/26), el servicio (asignar recursos, HU-18), el recurso (editar horario HU-19, desactivar/reactivar HU-16/17, y **reservar**, para serializarla con la desactivación). Operaciones del mismo objeto se ejecutan una tras otra. |
| D5 | **Reservar bloquea el recurso elegido** (`ResourceLookupService.lockActive`) antes de comprobar el traslape; la desactivación toma el mismo bloqueo, cuenta y cancela con él tomado. Así una reserva simultánea con una desactivación o entra antes (y se cancela) o llega tarde (y recibe `409`): **nunca** queda una reserva confirmada sobre un recurso inactivo. |
| D6 | **Asignación automática de recurso:** sin `resourceId`, se elige el primer recurso activo libre **por nombre** (determinista y explicable). Si otra petición lo toma en el mismo instante, esta recibe `409` y puede reintentar (no se reintenta sola). |
| D7 | **Tiempo:** los horarios de negocio son hora local de Bogotá (`America/Bogota`, sin horario de verano); se guardan como instantes UTC (`TIMESTAMPTZ`). El reloj es un bean (`Clock`) inyectable para poder fijarlo en las pruebas. |

## 3. Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| Índice único `(recurso, inicio)` | Solo evita inicios idénticos; no evita `10:00-11:00` y `10:30-11:30`. Las reservas pueden empezar en cualquier minuto libre. |
| `synchronized` / bloqueo en memoria | Solo vale con una instancia y se pierde al reiniciar. |
| Nivel de aislamiento `SERIALIZABLE` con reintentos | Obliga a reintentos en toda la aplicación y a manejar fallos de serialización; más difícil de explicar y de probar. |
| Bloqueo optimista (columna de versión) | Detecta el conflicto al guardar pero no resuelve «insertar dos filas nuevas que se traslapan» (no hay fila previa que versionar). |

## 4. Consecuencias

* **A favor:** correcto con varias instancias y ante reinicios; la regla no se puede saltar desde otro punto del código; el comportamiento es verificable con una prueba de carrera.
* **En contra / riesgos:** (a) depende de la extensión `btree_gist` (confiable desde PostgreSQL 13: la crea el dueño de la base sin ser superusuario). **Verificado en Render:** la migración V10 se aplicó y el flujo de reservas pasó en la prueba del servicio desplegado. (b) Un recurso muy disputado serializa sus reservas por el bloqueo de fila; con el volumen del proyecto no es problema. (c) **Lección de implementación:** una entidad ya cargada en la transacción oculta los cambios confirmados por otra transacción aunque la fila esté bloqueada; por eso `lockActive` lee `active` con una consulta nativa directa (`ResourceRepository.lockAndReadActive`). Un fallo así se detectó en la suite completa.
* **Decisión abierta:** si Calidad entiende «mismo servicio» literalmente (una sola reserva por servicio y horario), habría que cambiar la clave de la restricción de `resource_id` a `service_id`; es un cambio de una migración y de la regla de asignación.

## 5. Verificación

* Prueba de integración **contra PostgreSQL real**: dos clientes reservan el mismo horario a la vez → `[201, 409]` (`BookingCreationIntegrationTest`); la base rechaza dos reservas confirmadas traslapadas aunque se salte la aplicación y acepta una cancelada en el mismo horario.
* Reservar contra desactivar simultáneamente, repetido 8 veces por ejecución (`ResourceLifecycleIntegrationTest`): 24 de 24 correctas tras la corrección del bloqueo.
* Concurrencia en cancelar (`200` y `409`), asignar recursos y editar horarios (cada una con su prueba).
