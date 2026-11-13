# Informe Sprint 3 — Arquitectura de Software

**Proyecto:** Plataforma de Reservas de Servicios (Caso 14) · **Equipo de Arquitectura:** Simon Betancur, Juan Esteban González, Santiago Rendón · **Fecha:** 13 de noviembre de 2026
**Repositorio:** <https://github.com/Fabrica-escuela-proyectos/Proyecto2026-2> · **Aplicación desplegada:** <https://proyecto2026-2-5zoo.onrender.com> · **API interactiva (Swagger):** <https://proyecto2026-2-5zoo.onrender.com/swagger-ui.html>

> **Cómo leer este informe:** cada sección abre con su estado y cierra con una tabla de **evidencias** con enlaces al código y a los documentos de origen. Las rutas son relativas al repositorio. El informe del Sprint 2 (diagramas, CI/CD, OWASP, APIs) sigue vigente: [`informe-arquitectura-sprint-2.md`](../sprint-2/informe-arquitectura-sprint-2.md); aquí se reporta **lo que agregó el Sprint 3**.

## Resumen

| # | Entregable del Sprint 3 | Estado | Evidencia |
|---|---|---|---|
| 1 | HU pendientes del Sprint 2 (07, 10, 11, 12, 15, 21, 27) | **Cumplido** y desplegado | [§1](#1-hu-completadas) |
| 2 | HU nuevas de reportes (29 a 34) | **Cumplido**: módulo `report`, 6 endpoints | [§2](#2-módulo-de-reportes-hu-29-a-34) |
| 3 | Confirmación de supuestos con Calidad y cambios pedidos | **Cumplido**: respondidos el 6-nov; P12 y P5 implementados | [§3](#3-respuestas-de-calidad-p12-y-p5) |
| 4 | Decisiones de arquitectura nuevas (ADR) | **Cumplido**: ADR-008 y ADR-009 | [§4](#4-arquitectura-y-decisiones-nuevas) |
| — | Soporte: APIs · pruebas y calidad · despliegue · pendientes | Ver secciones | [§5](#5-apis-rest) · [§6](#6-pruebas-y-calidad) · [§7](#7-despliegue) · [§8](#8-pendientes) |

**En una frase:** el Sprint 3 completó las 7 historias de edición y mantenimiento que faltaban del Sprint 2, agregó las 6 de reportes en un módulo nuevo de solo lectura, y aplicó los dos cambios que pidió Calidad al revisar los supuestos (ocultar los datos del cliente eliminado y enviar un correo cuando le cancelan una reserva). Con esto el backend queda **funcionalmente completo: las 34 historias de usuario del proyecto**.

---

## 1. HU completadas

Las siete historias que en el Sprint 2 quedaron fuera del compromiso (Tier 3) se implementaron al inicio del Sprint 3, con pruebas unitarias y de integración, y están desplegadas.

| HU | Qué hace | Endpoint | Nota de diseño |
|---|---|---|---|
| **07** Editar negocio | Descripción, dirección, tipo y teléfono; el cliente los ve en el catálogo | `PATCH /api/v1/businesses/{id}` | Edición parcial; tipo y teléfono como listas/formatos cerrados (migración V13) |
| **10** Editar servicio | Cambiar nombre, duración y precio | `PATCH /api/v1/services/{id}` | **Las reservas ya hechas conservan su precio** (copia, ADR-006) |
| **11** Desactivar servicio | Con confirmación si tiene reservas futuras | `POST /api/v1/services/{id}/deactivation` | Mismo patrón que HU-16: `409 CONFIRMATION_REQUIRED` |
| **12** Reactivar servicio | Vuelve a estar disponible | `POST /api/v1/services/{id}/reactivation` | Idempotente; no restaura las reservas canceladas |
| **15** Editar recurso | Cambiar nombre y tipo | `PATCH /api/v1/resources/{id}` | Nombre único por negocio; el negocio no cambia |
| **21** Bloquear un horario puntual | Cierres de día completo o por rango | `POST`/`GET`/`DELETE /api/v1/resources/{id}/closures` | Tabla `resource_closures` con `EXCLUDE` anti-traslape (V14); el horario semanal no cambia |
| **27** Completar reserva | Marcar como completada tras su hora de fin | `POST /api/v1/bookings/{id}/completion` | Solo el proveedor dueño; cuenta para los reportes |

| Evidencia | Dónde |
|---|---|
| Contratos y reglas para Calidad | [`endpoints-sprint-3.md`](../api/endpoints-sprint-3.md) |
| Código de los controladores y servicios | `reservas-backend/src/main/java/.../{provider,service,resource,reservation}/` |
| Migraciones nuevas | [`V13`](../../reservas-backend/src/main/resources/db/migration/V13__add_business_profile_fields.sql), [`V14`](../../reservas-backend/src/main/resources/db/migration/V14__create_resource_closures_table.sql) |

## 2. Módulo de reportes (HU 29 a 34)

**Decisión ([ADR-008](../arquitectura/adr/ADR-008-modulo-de-reportes.md)):** un módulo nuevo `report`, **de solo lectura y sin tablas propias**, que lee por interfaces de los demás módulos; las sumas y conteos los hace la base de datos. Queda en la cima del grafo de dependencias (nadie depende de él) y no lo rompe.

| HU | Reporte | Endpoint (`GET`) | Rol |
|---|---|---|---|
| 29 | Ocupación por recurso | `/businesses/{id}/reports/resource-occupancy` | Proveedor dueño |
| 30 | Ocupación del negocio por día o semana | `/businesses/{id}/reports/occupancy?groupBy=DAY\|WEEK` | Proveedor dueño |
| 31 | Reservas por estado | `/businesses/{id}/reports/bookings-by-status` | Proveedor dueño |
| 32 | Servicios más reservados | `/businesses/{id}/reports/top-services` | Proveedor dueño |
| 33 | Valor de las reservas | `/businesses/{id}/reports/booking-value` | Proveedor dueño |
| 34 | Resumen de la plataforma | `/admin/reports/platform-summary` | Administrador (con MFA) |

**Ocupación** = minutos reservados (confirmadas + completadas) ÷ minutos disponibles (horario semanal vigente de los recursos activos, menos sus cierres) × 100. El periodo es `from`/`to` por día de Bogotá; por defecto los últimos 30 días, máximo 366.

| Evidencia | Dónde |
|---|---|
| Decisión del módulo | [`ADR-008`](../arquitectura/adr/ADR-008-modulo-de-reportes.md) |
| Contratos y definición de ocupación | [`endpoints-sprint-3.md`](../api/endpoints-sprint-3.md) §2 |
| Prueba con números exactos | `ReportIntegrationTest` (ocupación 3600/180 min = 5,0 %, conteos, valor, ranking) |

## 3. Respuestas de Calidad (P12 y P5)

Calidad respondió el **6 de noviembre** a los 18 supuestos ([`supuestos-para-calidad.md`](supuestos-para-calidad.md)): casi todo confirmado tal como estaba. Pidió **dos cambios**, ambos implementados:

### P12 — Datos del cliente eliminado
Al eliminar una cuenta, sus reservas dejan de mostrar el nombre y el correo del cliente: aparecen como **«Cliente eliminado»**. El resto del historial (servicio, recurso, horario, precio, estado) se conserva. La migración **V15** aplicó lo mismo a las reservas de cuentas ya eliminadas. (Adenda de [ADR-006](../arquitectura/adr/ADR-006-historial-snapshots-y-cancelaciones.md).)

### P5 — Correo al cliente cuando le cancelan una reserva
**Decisión ([ADR-009](../arquitectura/adr/ADR-009-avisos-por-correo.md)):** un módulo nuevo `notification` escucha el evento `BookingsCancelled` **después de que la transacción confirma y en otro hilo**, y envía un correo por la **API HTTPS de Brevo** (Render gratuito bloquea el envío por SMTP). Aplica a toda cancelación que **no** hace el propio cliente: proveedor (HU-26), servicio o recurso desactivado (HU-11/16), cierre (HU-21) y proveedor eliminado (HU-28). Si Brevo falla, la cancelación sigue siendo válida y queda en la auditoría; ese correo no se reintenta. **Verificado de punta a punta en Render el 2026-11-13.**

| Evidencia | Dónde |
|---|---|
| Respuestas de Calidad (comentarios + estado) | [`supuestos-para-calidad.md`](supuestos-para-calidad.md) |
| Decisión del correo | [`ADR-009`](../arquitectura/adr/ADR-009-avisos-por-correo.md) |
| Pruebas | `CancellationEmailIntegrationTest`, `CancellationNotifierTest`, `EmailSendersTest`, `AccountDeletionIntegrationTest` |
| Cómo configurar Brevo en Render | [`guia-despliegue-render.md`](../guia-despliegue-render.md) §4.3 |

## 4. Arquitectura y decisiones nuevas

El estilo no cambió (monolito modular). El Sprint 3 agregó **dos módulos** (`report`, `notification`), ambos encima del grafo, que lo mantiene **sin ciclos** (verificado por script).

| ADR | Decisión | Atributo de calidad | Estado |
|---|---|---|---|
| [008](../arquitectura/adr/ADR-008-modulo-de-reportes.md) | Reportes como módulo de solo lectura sin tablas | Mantenibilidad, no duplicar datos | Propuesto |
| [009](../arquitectura/adr/ADR-009-avisos-por-correo.md) | Correo tras confirmar, asíncrono, por API de Brevo | Disponibilidad (el correo no bloquea ni deshace la cancelación) | Propuesto |

Además, adendas a [ADR-005](../arquitectura/adr/ADR-005-anti-overbooking-y-concurrencia.md) (cierres con `EXCLUDE` y bloqueo compartido del servicio) y a [ADR-006](../arquitectura/adr/ADR-006-historial-snapshots-y-cancelaciones.md) (cliente eliminado).

| Evidencia | Dónde |
|---|---|
| Diagramas (generados del código): componentes, paquetes, despliegue | [`docs/arquitectura/diagramas/`](../arquitectura/diagramas) |
| Módulos sin ciclos | `python docs/arquitectura/verificar_dependencias.py` |
| Informe completo del proyecto y guía de sustentación | [`informe-final-proyecto.md`](../entrega-final/informe-final-proyecto.md), [`guia-sustentacion.md`](../entrega-final/guia-sustentacion.md) |

## 5. APIs REST

**48 endpoints** (15 más que el Sprint 2) en 7 módulos de negocio. Los nuevos: edición de negocio/servicio/recurso, desactivar/reactivar servicio, cierres, completar reserva y los 6 de reportes. El módulo `notification` no expone endpoints (reacciona a eventos).

| Evidencia | Dónde |
|---|---|
| Matriz de los 48 endpoints con rol y todos los DTO (**generada del código**) | [`referencia-api-sprint-2.md`](../api/referencia-api-sprint-2.md) |
| Swagger en vivo (9 grupos) | <https://proyecto2026-2-5zoo.onrender.com/swagger-ui.html> |

## 6. Pruebas y calidad

| Medida | Valor | Fuente |
|---|---|---|
| Pruebas automáticas | **833**, 0 fallos (483 unitarias + 350 de integración con PostgreSQL real) | `./mvnw clean test` y el pipeline |
| Cobertura | **97,5 % de líneas / 91,1 % de ramas** | JaCoCo / SonarCloud |
| Quality Gate · bugs · vulnerabilidades · *code smells* · deuda | **OK** · 0 · 0 · 0 · 0 min | SonarCloud (commit `6dab10a`) |
| Prueba de extremo a extremo en Render | **119 peticiones, 166 aserciones, 0 fallos** (2026-11-03, con el código del Sprint 3) | [`resultados-pruebas-sprint-3.md`](../resultados-pruebas-sprint-3.md) |
| Correo de cancelación en Render | **Verificado** (2026-11-13): el cliente recibió el aviso por Brevo | [`resultados-pruebas-sprint-3.md`](../resultados-pruebas-sprint-3.md) |

## 7. Despliegue

Sin cambios de topología respecto al Sprint 2: cómputo en **Render**, base de datos en **Neon** (desde el 25-oct), despliegue desde GitHub Actions. Lo nuevo: dos variables de entorno para el correo (`BREVO_API_KEY`, `NOTIFICATION_SENDER_EMAIL`); sin ellas, los avisos solo se escriben en el log y todo lo demás funciona igual.

| Evidencia | Dónde |
|---|---|
| Diagrama de despliegue (con Neon y el módulo de correo) | [`diagrama-despliegue.png`](../arquitectura/diagramas/diagrama-despliegue.png) |
| Configurar Brevo | [`guia-despliegue-render.md`](../guia-despliegue-render.md) §4.3 |

## 8. Pendientes

| Pendiente | Tipo | Responsable |
|---|---|---|
| Aclarar con Calidad P7 (¿se quita el cierre de día completo, que el criterio de HU-21 sí pide?) y P1 (¿HU-30 a HU-34 entran al Sprint 3? no existen en Azure) | Alcance | Equipo con Calidad |
| Ratificar los ADR 004–009 (hoy «Propuesto», ya implementados) | Gobernanza | Los tres de Arquisoft |
| Subir a Azure la tarea del componente de correo; proteger `main` | Proceso / seguridad | Simon |
| Pruebas de Calidad del sprint | Verificación | Calidad (17–23 nov) |

## Cómo verificar en 5 minutos

| Qué | Cómo |
|---|---|
| La aplicación está viva | `curl https://proyecto2026-2-5zoo.onrender.com/actuator/health` (puede tardar ≈ 2 min si estaba dormida) |
| La API y sus contratos | Abrir el [Swagger](https://proyecto2026-2-5zoo.onrender.com/swagger-ui.html) |
| Las 833 pruebas | `cd reservas-backend && ./mvnw clean test` (necesita Docker) |
| El flujo de extremo a extremo | `npx newman run docs/postman/Reservas-Sprint2.postman_collection.json -e docs/postman/Reservas-Render.postman_environment.json --timeout-request 150000` |
| Módulos sin ciclos | `python docs/arquitectura/verificar_dependencias.py` |

**Bitácora del sprint:** [`handoff-contexto.md`](../sprint-2/handoff-contexto.md) §9.
