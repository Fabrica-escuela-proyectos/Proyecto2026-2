# ADR-006 — Historial de reservas, copias («snapshots») y modelo de cancelación

* **Estado:** Propuesto. Implementado en el Sprint 2; pendiente de revisión del equipo y de una consulta a Calidad/docente sobre retención de datos personales.
* **Fecha:** 2026-10-10
* **Responsable:** Simon Betancur.
* **Decisión:** una reserva es un **registro histórico autosuficiente**: guarda copias de los datos que el usuario vio al reservar, sus referencias a otras tablas pasan a `NULL` si el objeto original se elimina (nunca se borra la reserva en cascada ni se bloquea la eliminación), y una cancelación se modela como **estado `CANCELADA` + origen + motivo**, no como estados compuestos.

## 1. Contexto

HU-28 exige eliminar cuentas (cliente, proveedor o por un administrador) y «los servicios del proveedor quedan eliminados», pero también cancelar las reservas futuras y no perder el historial (las reservas pasadas deben seguir existiendo). Los criterios de aceptación hablan de estados como «Cancelada por proveedor» y «Cancelada por eliminación de cuenta»; HU-26 pide que el cliente vea el motivo. Además HU-22 pide que la reserva guarde lo que el cliente pagó aunque el servicio se edite después.

## 2. Decisión

| # | Decisión |
|---|---|
| D1 | **Copias en la reserva:** `client_email`, `client_name`, `business_name`, `service_name`, `resource_name` y `price_cop` se copian al crearla. Editar o eliminar el servicio, el recurso, el negocio o la cuenta no cambia lo que muestra una reserva existente (HU-23, HU-24). |
| D2 | **Referencias que se anulan, no que bloquean ni borran:** `client_id`, `business_id`, `service_id` y `resource_id` son `ON DELETE SET NULL`. Eliminar un usuario o un negocio **no** impide la operación (no hay `RESTRICT`) y **no** arrastra las reservas (no hay `CASCADE`). Convención general en [`convenciones-bd.md`](../../bd/convenciones-bd.md). |
| D3 | **Cancelación = estado + origen + motivo.** `status ∈ {CONFIRMADA, CANCELADA, COMPLETADA}`; `cancel_origin ∈ {CLIENTE, PROVEEDOR, ELIMINACION_CUENTA, RECURSO_NO_DISPONIBLE, SERVICIO_NO_DISPONIBLE}`; `cancel_reason` es texto libre de quien cancela; `cancelled_at` la fecha. Las etiquetas del criterio de aceptación («Cancelada por proveedor») se obtienen combinando estado y origen sin multiplicar estados. |
| D4 | **Regla de antelación (1 h) solo para el cliente.** Las cancelaciones que no decide el cliente (proveedor, eliminación de cuenta, recurso desactivado) no están sujetas a ella; el proveedor tampoco puede cancelar una reserva que ya empezó. |
| D5 | **Cancelaciones masivas** (desactivar recurso, eliminar cuenta) son un único `UPDATE` sobre las reservas **futuras y confirmadas** dentro de la transacción de la operación que las provoca; las pasadas, completadas y ya canceladas no se tocan. |
| D6 | **Las notificaciones están fuera de alcance:** el «aviso» que piden las HU-25/26/28 queda como evento de auditoría (`CANCELACION_RESERVA`) con las partes afectadas. |
| D7 | **Los usuarios no se «desactivan», se eliminan:** HU-05 ya elimina la fila; el historial se sostiene con las copias, no con una baja lógica. |

## 3. Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| `ON DELETE CASCADE` desde usuario/negocio a reservas | Borraría el historial del otro lado (el cliente perdería sus reservas pasadas al eliminarse un proveedor y viceversa). |
| `ON DELETE RESTRICT` | Impediría eliminar cuentas con historial, contra HU-05/HU-28. |
| Estados compuestos (`CANCELADA_POR_PROVEEDOR`…) | Se multiplican con cada motivo nuevo y complican los filtros; estado + origen es ortogonal. |
| Baja lógica de usuarios (`enabled = false`) | Conserva datos personales sin necesidad y deja correos «ocupados»; HU-28 pide eliminar la cuenta. |
| Unir con `users`/`services` en cada listado | El historial dependería de filas que pueden no existir; más lento y frágil. |

## 4. Consecuencias

* **A favor:** eliminar cuentas, servicios y negocios es seguro y barato; los listados no necesitan uniones; el proveedor y el cliente siguen viendo el historial coherente; el origen permite métricas y reglas distintas por tipo de cancelación.
* **En contra:** (a) **retención de datos personales:** al eliminar una cuenta, el nombre y el correo del cliente **permanecen en las reservas históricas** que el proveedor puede ver. Es consecuencia directa de «conservar el historial» (HU-28), pero conviene confirmar con el docente/Calidad si el modelo debe anonimizarlos (p. ej. sustituir por «Cliente eliminado» tras N días); queda como supuesto S-27 en [decisiones-y-supuestos-sprint-2.md](../decisiones-y-supuestos-sprint-2.md). (b) Las copias pueden quedar desactualizadas respecto al objeto original, y eso es lo deseado. (c) Las reservas canceladas antes de la migración V12 tienen origen nulo.
* **Migraciones:** V10 (tabla `bookings`), V11 (`client_name`, con relleno de lo existente), V12 (`cancel_origin`).

## 5. Verificación

Pruebas de integración: al eliminar un cliente o un proveedor (administrador con MFA) las futuras quedan `CANCELADA` con origen `ELIMINACION_CUENTA`, las pasadas/completadas/canceladas se conservan con sus copias y sus referencias en `NULL`, el proveedor sigue viendo la reserva (`AccountDeletionIntegrationTest`); cambiar el precio o el nombre del servicio no altera una reserva ya hecha (`BookingCreationIntegrationTest`); el nombre y el correo del cliente son los del momento de reservar (`BusinessBookingsIntegrationTest`). La colección de Postman repite el flujo contra Render.
