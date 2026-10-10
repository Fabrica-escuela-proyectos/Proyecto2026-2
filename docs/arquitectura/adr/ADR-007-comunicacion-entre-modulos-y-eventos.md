# ADR-007 — Comunicación entre módulos: interfaces de consulta, orquestación y eventos de dominio

* **Estado:** Propuesto. Implementado en el Sprint 2 como aplicación concreta de [ADR-003](ADR-003-modularidad-e-interfaces.md); pendiente de revisión del equipo.
* **Fecha:** 2026-10-10
* **Responsable:** Simon Betancur.
* **Decisión:** los módulos del monolito se hablan **solo por interfaces de la capa `application`** (y, en un caso, por un evento de dominio síncrono). Ningún módulo importa las entidades ni los repositorios de otro. Las dependencias forman un grafo **sin ciclos**. Lo que cruza varios módulos lo **orquesta el módulo que está más «arriba»** del grafo.

## 1. Contexto

ADR-001 fijó un monolito modular y ADR-003 las interfaces entre módulos, pero con cinco módulos de negocio (`identity`, `provider`, `service`, `resource`, `reservation`) aparecieron casos que lo ponen a prueba: una reserva necesita datos de servicio, recurso, horario, negocio y cliente; desactivar un recurso debe cancelar reservas; eliminar un usuario debe cancelar reservas sin que `identity` conozca a `reservation`; la disponibilidad debe descontar reservas que todavía no existen cuando se escribe el motor.

## 2. Decisión

| # | Decisión |
|---|---|
| D1 | **Contratos `*Lookup` / `*Directory` / `*Access` de solo lectura** en la capa `application` de cada módulo, con registros (`record`) simples en lugar de entidades: `BusinessDirectoryService`, `BusinessAccessService`, `BusinessSettingsService` (provider); `ResourceLookupService`, `ResourceScheduleLookup`, `ResourceLifecycleService` (resource); `ServiceLookupService` (service); `IdentityService`, `AccountStatusService`, `UserDirectoryService` (identity). Un módulo consumidor recibe `ResourceInfo`, `ServiceInfo`, `BusinessInfo`…, nunca la entidad JPA. |
| D2 | **Sin `@ManyToOne` entre módulos:** las referencias son columnas `UUID` con `REFERENCES` en SQL. La integridad la mantiene la base; el mapeo objeto-relacional de un módulo no conoce a otro. |
| D3 | **Grafo acíclico.** Dependencias reales (de las importaciones del código, 2026-10-10): `identity` → (`audit`); `provider` → `identity`; `resource` → `provider`, `identity`; `service` → `resource`, `provider`, `identity`; `reservation` → `service`, `resource`, `provider`, `identity`. `audit` no depende de nadie. |
| D4 | **Orquestar arriba.** La desactivación de un recurso (HU-16) toca recursos y reservas: la orquesta `reservation` (`ResourceDeactivationService`), que llama a `ResourceLifecycleService` de `resource`. Si la orquestara `resource`, tendría que importar `reservation` y se crearía un ciclo. |
| D5 | **Punto de extensión inverso por inyección de colección:** el motor de disponibilidad (`service`) define la interfaz `BusyTimeSource`; `reservation` la implementa (`BookingBusyTimeSource`) y Spring la inyecta (`ObjectProvider`). `service` descuenta reservas sin conocer el módulo `reservation` (que es el que depende de `service`, no al revés). Sin ninguna implementación el motor funciona igual (útil para probarlo aislado). |
| D6 | **Evento de dominio síncrono para «algo ocurrió y otros deben reaccionar»:** al eliminar un usuario, `identity` publica `UserDeletionRequested` **dentro de la transacción** (`ApplicationEventPublisher`) y `reservation` lo escucha (`AccountDeletionBookingListener`). `identity` no importa `reservation`. Al ser síncrono y transaccional, si el oyente falla se deshace también la eliminación (todo o nada, HU-28). |
| D7 | **Un solo manejador de errores** (`GlobalExceptionHandler`) traduce todas las excepciones de dominio al formato `ApiError`; los controladores no construyen errores. |
| D8 | **Reloj y zona de negocio** como bean (`TimeConfig.businessClock`, `America/Bogota`), inyectado donde se necesita «ahora». |

## 3. Desviaciones conocidas de ADR-003 (deuda explícita)

La revisión de importaciones del 2026-10-10 muestra que **no hay entidades ni repositorios de un módulo usados en otro**, pero sí estas excepciones, todas pequeñas y anteriores o ajenas a las interfaces nuevas:

| Dónde | Importa | Por qué importa / qué haría falta |
|---|---|---|
| `provider` (registro de proveedor, HU-03) | `identity.infrastructure.RegistrationRateLimiter`, `TooManyRequestsException`, `identity.domain.DuplicateEmail/PhoneException`, `identity.domain.RoleName` | Código del Sprint 1: el registro de proveedor reutiliza los internos del registro de usuario. Debería pasar por una interfaz de `identity` (p. ej. `UserProvisioningService`, que ya existe). |
| `common.error.GlobalExceptionHandler` | Excepciones `domain` de **todos** los módulos | Un manejador único implica que `common` conoce las excepciones de cada módulo (dependencia «hacia arriba»). Alternativa: un `@RestControllerAdvice` por módulo. Se aceptó por simplicidad y uniformidad del formato de error. |
| `reservation` | `service.domain.ServiceNotAvailableException` | Reutiliza la excepción de «servicio no disponible» para dar el mismo mensaje y código que HU-20. |
| Todos los módulos | `audit.domain.AuditEventType` (enum) | `audit` actúa de *shared kernel*: el catálogo de eventos es común. |

Ninguna crea ciclos entre módulos de negocio. Se anotan para decidir si se corrigen en el Sprint 3.

## 4. Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| Que cada módulo acceda al repositorio del otro | Rompe ADR-003: cualquier cambio de una tabla afecta a todos. |
| Un único «módulo de reservas» grande que contenga servicios y recursos | Mezcla responsabilidades que el proveedor gestiona por separado (HU-09, 14, 18, 19) y vuelve intratable el equipo en paralelo. |
| Eventos asíncronos (cola, `@Async`, `@TransactionalEventListener(AFTER_COMMIT)`) para la eliminación de cuentas | Perdería la atomicidad que exige HU-28: podría eliminarse el usuario y fallar la cancelación. Sería adecuado para las notificaciones, hoy fuera de alcance. |
| Servicios separados (microservicios) | Fuera del alcance del curso y de ADR-001. |

## 5. Consecuencias

* **A favor:** cada módulo se puede probar con las interfaces simuladas (las pruebas unitarias usan Mockito sobre ellas); un cambio de tabla queda dentro de su módulo; el futuro paso a servicios separados tiene cortes naturales.
* **En contra:** más clases pequeñas (interfaz + implementación + `record`); la lectura por interfaz hace una consulta por llamada (se aceptó por claridad; no hay problema de rendimiento al volumen actual); un evento síncrono acopla el tiempo de la eliminación al de las cancelaciones.

## 6. Verificación

Las importaciones se pueden revisar con la búsqueda del Anexo de [`decisiones-y-supuestos-sprint-2.md`](../decisiones-y-supuestos-sprint-2.md); [`docs/arquitectura/diagramas/`](../diagramas/) contiene los diagramas de componentes y paquetes del Sprint 1 (pendiente actualizarlos con `service`, `resource` y `reservation`: tarea `DOC-03`). Pruebas: `AccountDeletionBookingListenerTest` y `UserManagementServiceImplTest` (el evento se publica antes de borrar, y el fallo del oyente impide borrar), `AccountDeletionIntegrationTest` (de punta a punta).
