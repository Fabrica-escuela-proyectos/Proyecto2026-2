# Entregable de Arquitectura de Software — Sprint 2 (base del informe)

Estado a **2026-10-12**. Corte Arquisoft/BD: **13 de octubre**; review: 20 de octubre. Este documento no es el informe: es el **mapa de evidencias** para armarlo. Cada sección dice qué pide el curso, qué tenemos, dónde está, qué falta y cómo justificarlo.

El Sprint 2 de Arquitectura de Software pide (Lineamientos §3.7): **diagrama de despliegue · APIs priorizadas · al menos tres APIs REST · CI/CD inicial con GitHub Actions · identificación de vulnerabilidades con OWASP Top Ten · documentación de las APIs**.

## 0. Resumen: ¿qué cubre cada entregable?

| Entregable del curso | Estado | Evidencia principal | Qué falta |
|---|---|---|---|
| Diagrama de despliegue | **Hecho (2026-10-12)** | [`diagrama-despliegue.png`](../arquitectura/diagramas/diagrama-despliegue.png) + [tabla de conexiones](../arquitectura/diagramas/conexiones-diagrama-despliegue.md) | Revisión de Juan Esteban y Santiago |
| APIs priorizadas | **Hecho** | [Plan §2](plan-de-trabajo-sprint-2.md) (niveles Tier 1/2/3), 15 HU de reserva implementadas | Justificar en el informe el orden elegido (§3 de este documento) |
| Al menos tres APIs REST | **Hecho, con holgura** | **33 endpoints REST** en 18 controladores y 5 módulos de negocio ([referencia](../api/referencia-api-sprint-2.md)) | — |
| CI/CD inicial con GitHub Actions | **Hecho y funcionando** | [`build.yml`](../../.github/workflows/build.yml): pruebas → Sonar → build → despliegue a Render con *smoke test* | Protección de `main` (hoy **no** está activa) |
| Vulnerabilidades con OWASP Top Ten | **Hecho (revisión + correcciones)** | [`owasp-top10-sprint-2.md`](../seguridad/owasp-top10-sprint-2.md) | Pendientes de seguridad (§6); es una revisión, **no** una prueba de penetración |
| Documentación de las APIs | **Hecho** | Swagger UI + [referencia generada](../api/referencia-api-sprint-2.md) + [errores](../api/errores-api-sprint-2.md) + [contratos](../api/endpoints-sprint-2.md) + [Postman](../postman/README.md) | — |
| ADR (continuidad del Sprint 1: mínimo 3) | **7 ADR** | [`docs/arquitectura/adr/`](../arquitectura/adr) | ADR-004 a 007 están en estado *Propuesto*: faltan las aprobaciones |

## 1. Dónde está cada documento

| Qué buscas | Dónde |
|---|---|
| **Arquitectura general** (estilo, módulos, interfaces, objetivos) | [`docs/arquitectura/arquitectura-sprint-1.md`](../arquitectura/arquitectura-sprint-1.md) (base) y [`interfaces-modulos-sprint-1.md`](../arquitectura/interfaces-modulos-sprint-1.md) |
| **Decisiones (ADR)** | [`docs/arquitectura/adr/`](../arquitectura/adr): 001 monolito modular · 002 autenticación y sesiones · 003 módulos e interfaces · 004 MFA · 005 anti-overbooking · 006 historial y cancelaciones · 007 comunicación entre módulos |
| **Registro de decisiones y supuestos** (lo que supusimos hasta que Calidad/PO digan algo) | [`decisiones-y-supuestos-sprint-2.md`](../arquitectura/decisiones-y-supuestos-sprint-2.md): 32 supuestos S-01…S-32, deuda técnica y preguntas |
| **Diagramas** | [`docs/arquitectura/diagramas/`](../arquitectura/diagramas): **despliegue** (nuevo), componentes, paquetes y sus tablas de conexiones |
| **Endpoints y contratos** | Reglas de negocio con ejemplos: [`endpoints-sprint-2.md`](../api/endpoints-sprint-2.md). Matriz de acceso y DTO (generada del código): [`referencia-api-sprint-2.md`](../api/referencia-api-sprint-2.md). Errores: [`errores-api-sprint-2.md`](../api/errores-api-sprint-2.md). Sprint 1: `endpoints-sprint-1.md`, `errores-api-sprint-1.md` |
| **Swagger** | En vivo: `/swagger-ui.html` (local y Render). Cómo mantenerlo: [`guia-swagger-openapi.md`](../api/guia-swagger-openapi.md) |
| **Pruebas** | Resultados: [`resultados-pruebas-sprint-2.md`](../resultados-pruebas-sprint-2.md). Postman/Newman: [`docs/postman/`](../postman/README.md). Código: `reservas-backend/src/test` (655 pruebas) |
| **OWASP Top 10** | [`docs/seguridad/owasp-top10-sprint-2.md`](../seguridad/owasp-top10-sprint-2.md) |
| **Base de datos** | Migraciones `reservas-backend/src/main/resources/db/migration` (V1–V12); convenciones [`docs/bd/convenciones-bd.md`](../bd/convenciones-bd.md); modelo formal del equipo de BD en `docs/bd/modelo/` |
| **Despliegue y CI/CD** | [`guia-despliegue-render.md`](../guia-despliegue-render.md), [`build.yml`](../../.github/workflows/build.yml), [`dependabot.yml`](../../.github/dependabot.yml) |
| **Estado vivo y bitácora** | [`handoff-contexto.md`](handoff-contexto.md) |

## 2. Arquitectura: estilo y módulos (para la sección «Arquitectura de la solución», 25 % de la rúbrica)

**Estilo:** monolito modular sobre Spring Boot 4.1.1 / Java 17 / PostgreSQL 16 ([ADR-001](../arquitectura/adr/ADR-001-monolito-modular.md)). Un solo artefacto desplegable, con módulos por dominio que se hablan solo por interfaces de la capa `application` y un evento de dominio síncrono ([ADR-003](../arquitectura/adr/ADR-003-modularidad-e-interfaces.md), [ADR-007](../arquitectura/adr/ADR-007-comunicacion-entre-modulos-y-eventos.md)). Cada módulo se organiza en `controller` / `application` / `infrastructure` / `domain`.

| Módulo | Responsabilidad | HU | Endpoints | Tablas (migración) |
|---|---|---|---:|---|
| `identity` | Registro de clientes, login, logout, sesiones, roles, MFA TOTP, eliminación de cuentas | 01, 02, 04, 05, 28 | 8 | `users`, `roles`, `user_roles` (V1), `sessions` (V2), `mfa` (V4) |
| `provider` | Registro de proveedor y negocio, perfil, antelación mínima de reserva | 03, 06, 08 | 5 | `providers`, `businesses` (V3, V6) |
| `service` | Servicios, catálogo, asignación de recursos y **motor de disponibilidad** | 09, 13, 18, 20 | 7 | `services` (V5), `service_resources` (V8) |
| `resource` | Recursos, horarios semanales, desactivar/reactivar | 14, 19, 16, 17 | 6 | `resources` (V7), `resource_availability` (V9) |
| `reservation` | Crear, consultar y cancelar reservas; orquesta la desactivación de recursos y la eliminación de cuentas | 22, 23, 24, 25, 26 (+16, 28) | 7 | `bookings` (V10–V12) |
| `audit` | Eventos de auditoría (sin secretos), usados por todos | transversal | — | `audit_logs` |
| `common` | Formato de error `ApiError`, configuración, validaciones, filtros | transversal | — | — |

Dependencias: `identity` ← `provider` ← `resource` ← `service` ← `reservation`; sin ciclos (se comprueba con `python docs/arquitectura/verificar_dependencias.py`). Las desviaciones conocidas están listadas con su justificación en ADR-007 §3.

### Decisiones clave y cómo justificarlas (criterios técnicos)

| Decisión | Atributo de calidad | Justificación en una frase | Documento |
|---|---|---|---|
| Monolito modular, no microservicios | Simplicidad, mantenibilidad, costo | Equipo de 3 con un solo despliegue gratuito; los cortes por módulo dejan abierta la separación futura | ADR-001, ADR-003 |
| JWT de 1 h + tabla de sesiones | Seguridad, revocación | El token es sin estado, pero la sesión se puede revocar (logout efectivo) | ADR-002 |
| MFA TOTP obligatorio para administradores | Seguridad | Las operaciones sensibles (cambiar roles, eliminar cuentas) las hace el rol más expuesto | ADR-004 *(Propuesto)* |
| Anti-overbooking **en la base de datos** (`EXCLUDE` + bloqueos de fila) | Integridad, correctitud bajo concurrencia | Una verificación en memoria no es atómica; la base lo garantiza aunque haya varias instancias | ADR-005 |
| Reservas con copias («snapshots») y `ON DELETE SET NULL` | Trazabilidad, historial | Eliminar una cuenta no puede borrar ni bloquear el historial del otro lado | ADR-006 |
| Interfaces `*Lookup/*Directory/*Access` entre módulos y evento síncrono de eliminación | Modificabilidad, bajo acoplamiento | Cambiar una tabla no afecta a otros módulos; la eliminación sigue siendo atómica (todo o nada) | ADR-007 |
| Formato de error uniforme con `traceId` | Operabilidad, diagnóstico | Cada fallo reportado se encuentra en el log por su identificador | `errores-api-sprint-2.md` |
| Docker + despliegue por *hook* tras pasar el pipeline | Reproducibilidad, seguridad de la entrega | Solo llega a Render lo que pasó pruebas, Sonar y build | diagrama de despliegue |

## 3. APIs priorizadas y APIs REST

**Priorización (plan §2):** el criterio fue entregar el flujo completo de reserva de punta a punta antes que ampliar el catálogo.

| Nivel | HU | Estado |
|---|---|---|
| **Tier 1 (compromiso)** | 08, 09, 13, 14, 18, 19, 20, 22, 23, 24, 25 | **Hecho** y desplegado |
| **Tier 2** | 16, 17, 26, 28 | **Hecho** y desplegado |
| Tier 3 (Sprint 3) | 07, 10, 11, 12, 15, 21, 27 | Fuera del alcance acordado (HU-27 va con Report; HU-21 se cubre en parte con HU-16/17) |

**33 endpoints REST**: 4 públicos (login, registro de cliente, registro de proveedor, disponibilidad de un servicio), 8 para cualquier usuario autenticado, 15 de PROVEEDOR, 4 de CLIENTE y 2 de ADMINISTRADOR (con confirmación MFA). Detalle por ruta, rol y DTO en [`referencia-api-sprint-2.md`](../api/referencia-api-sprint-2.md).

**Documentación de las APIs (cuatro capas):** (1) **Swagger UI** interactivo con 8 grupos, 47 esquemas, errores como `ApiError` y ejemplos; (2) **referencia generada del código** (no se desactualiza); (3) **catálogo de errores** con el mensaje esperado por HU; (4) **colección de Postman** de 88 peticiones y 119 aserciones que sirve de prueba de extremo a extremo.

## 4. CI/CD con GitHub Actions

[`build.yml`](../../.github/workflows/build.yml) tiene 4 trabajos encadenados:

| Trabajo | Qué hace | Cuándo |
|---|---|---|
| Unit tests | `mvn verify` con JaCoCo; las pruebas de integración usan PostgreSQL real con Testcontainers | push a `main` y cada PR |
| SonarCloud analysis | Quality Gate, cobertura y vulnerabilidades | push a `main` y cada PR |
| Build | Empaqueta el JAR y lo sube como artefacto | push a `main` y cada PR |
| Deploy to Render | Dispara el *deploy hook* y espera a que `/actuator/health` responda `200` | **solo `main`** |

Evidencia a 2026-10-12: los últimos despliegues desde `main` terminan en *success*; el Quality Gate de Sonar está en **OK** (0 issues, 0 vulnerabilidades, cobertura 96,1 %). **Dependabot** abre PR semanales de Maven, Actions y Docker, y cada uno pasa por el mismo pipeline (el secreto `SONAR_TOKEN` existe también para Dependabot).

**Qué falta:** (1) **protección de la rama `main`** (verificado hoy: `protected = false`): PR obligatorio, revisión de otra persona y checks requeridos; es lo que dicta el flujo oficial del equipo y es un ajuste de GitHub, no de código; (2) un escaneo de vulnerabilidades de dependencias como paso del pipeline; (3) fijar las acciones de GitHub por SHA (decisión de costo/beneficio). Los contenedores y la orquestación son del Sprint 3.

## 5. Pruebas y calidad (soporta «Calidad interna», 25 %)

| Medida | Valor | Fuente |
|---|---|---|
| Pruebas automáticas | **655**, 0 fallos (383 unitarias + 272 de integración con PostgreSQL real) | `./mvnw clean test` y el pipeline |
| Cobertura | **96,1 %** (Sonar, proyecto completo) · 97,2 % de líneas / 92,2 % de ramas (JaCoCo local) | SonarCloud, `jacoco.csv` |
| Deuda técnica / vulnerabilidades / bugs / *code smells* | 0 / 0 / 0 / 0 | SonarCloud |
| Quality Gate | **OK** | SonarCloud |
| Prueba de extremo a extremo sobre lo desplegado | Colección de Postman contra Render: **88 peticiones, 0 fallos el 2026-10-10**; el **2026-10-12**, con el código endurecido desplegado, **88 peticiones y 119 aserciones, 0 fallos** (carpetas 00–07 y la 08 del administrador con MFA) | [`resultados-pruebas-sprint-2.md`](../resultados-pruebas-sprint-2.md) |
| Bugs de Calidad (#8 a #11) | Corregidos con regresión medida antes/después | [`cierre-pendientes-sprint-1.md`](cierre-pendientes-sprint-1.md); **los issues siguen abiertos en GitHub** |

Duda abierta con Calidad: cómo miden «complejidad < 50» (Sonar da 622 en total y 271 cognitiva; sin saber si es por método, por archivo o total no se puede afirmar que se cumple).

## 6. OWASP Top 10 (2021): qué cubrimos, qué falta y por qué

Fuente completa y evidencia: [`owasp-top10-sprint-2.md`](../seguridad/owasp-top10-sprint-2.md). **Método:** revisión de código y configuración, 655 pruebas automáticas, sondeos de solo lectura al servicio desplegado y correcciones. **No es una prueba de penetración** y el informe debe decirlo así.

| Riesgo | Estado | Qué hace la aplicación (resumen) | Qué falta |
|---|---|---|---|
| A01 Control de acceso | **Cubierto** | Rol por ruta (`@PreAuthorize`) y pertenencia validada en el servicio para cada recurso; mismo error para «no existe» y «no es tuyo» cuando revelarlo filtraría información | Prueba automática de la matriz completa de acceso (hoy la cubren las pruebas por HU y Postman) |
| A02 Fallas criptográficas | **Parcial** | BCrypt; JWT firmado; TLS terminado por Render | **Contraseña del admin demo publicada en las guías (SEC-01/02)**; secreto TOTP en claro en la BD (MFA-07) |
| A03 Inyección | **Cubierto** | Consultas parametrizadas (JPA), validación con límites en todo DTO, sondeo de inyección SQL sobre Render sin efecto | Prueba con un catálogo amplio de cargas de ataque |
| A04 Diseño inseguro | **Parcial** | Reglas de negocio en el servidor, anti-overbooking en la BD, límites de intentos **por IP real del cliente** (verificado en Render que detrás de Cloudflare se guardaba la IP del borde; corregido y **reverificado en Render el 2026-10-13**: se guarda la IP real), tope de cuerpo (64 KB), paginación con tope | Límite de tasa en rutas públicas (disponibilidad, crear reservas); los límites viven en memoria (una instancia) |
| A05 Configuración insegura | **Cubierto en código** | Sin perfil por defecto y arranque que falla con valores de relleno, contenedor sin root, cabeceras (CSP, Referrer, Permissions), CORS explícito, solo `/actuator/health` público | **Swagger activo en Render por decisión del equipo** (riesgo aceptado, S-30) |
| A06 Componentes vulnerables | **Parcial** | `jjwt` 0.13.0, Testcontainers 2.0.5, Dependabot semanal | Escaneo de vulnerabilidades como paso del pipeline |
| A07 Autenticación | **Parcial** | Política de contraseña, mensajes genéricos, bloqueo por fuerza bruta, MFA TOTP obligatorio para administradores | Cambio de contraseña (SP1-01), anti-replay del TOTP (MFA-06), recuperación de MFA (MFA-08), pedir la contraseña en `/mfa/setup` (SEC-04) |
| A08 Integridad | **Parcial** | CI con pruebas y Sonar, migraciones Flyway inmutables, `ddl-auto: validate`, sin deserialización insegura | **`main` sin protección de rama (verificado)** y commits subidos por la web (`Add files via upload`) |
| A09 Registro y monitoreo | **Parcial** | Auditoría de eventos sin secretos, `traceId`/`X-Request-Id` en errores y logs, logs JSON en `prod` | Alertas ante 429/403 repetidos; el `traceId` aún no se guarda en `audit_logs` |
| A10 SSRF | **No aplica** | El backend no hace llamadas salientes | Revisar si llegan las notificaciones (hoy fuera de alcance) |

**Justificación de lo que no se hizo:** el alcance del Sprint 2 pide *identificar* vulnerabilidades; las brechas restantes están priorizadas con tarea y esfuerzo en el plan de cierre del documento OWASP, y las dos de mayor riesgo (contraseña demo pública y `main` sin protección) dependen de acciones de administración que no están en el código.

## 7. Qué falta antes del corte del 13 de octubre

| # | Pendiente | Quién | Esfuerzo |
|---|---|---|---|
| 1 | **Redactar el informe** con la estructura de §8 (este documento aporta el contenido) | Arquisoft | 3–4 h |
| 2 | **Revisar el diagrama de despliegue** y aprobar los ADR 004–007 (cambiar su estado a *Aceptado*) | Juan Esteban y Santiago | 1 h |
| 3 | ~~Corrida completa de Postman contra Render (incluye la carpeta de administrador)~~ **hecha el 2026-10-12: 88 peticiones, 119 aserciones, 0 fallos** | — | — |
| 4 | **Rotar la contraseña del admin demo** y retirarla de [`guia-prueba-aplicacion-desplegada.md`](../guia-prueba-aplicacion-desplegada.md) (SEC-01/02) | Simon | 30 min |
| 5 | **Proteger `main`** (PR + revisión + checks requeridos) y crear `CODEOWNERS` | Simon (admin del repositorio) | 30 min |
| 6 | Cerrar los issues #8 a #11 de Calidad con referencia a la corrección, y revisar el PR #18 de BD | Simon | 30 min |
| 7 | Regenerar `diagrama-paquetes.png` con PlantUML (el `.puml` ya marca `provider`, `service`, `resource` y `reservation` como implementados; el PNG aún muestra el estado del Sprint 1) y confirmar el diagrama de componentes | Juan Esteban | 30 min |
| 8 | Decidir con Calidad las preguntas abiertas (§4 de [decisiones y supuestos](../arquitectura/decisiones-y-supuestos-sprint-2.md)), sobre todo S-27 y S-14 | Equipo | — |
| 9 | Fusionar los PR de Dependabot de uno en uno (opcional antes del corte) | Simon | 1 h |

## 8. Estructura sugerida del informe

1. **Introducción y alcance** (HU del Sprint 2, niveles de prioridad, qué quedó para el Sprint 3).
2. **Arquitectura de la solución:** estilo, módulos (tabla de §2), figuras de **componentes, paquetes y despliegue**; ADR 001–007 con su justificación (§2).
3. **APIs priorizadas y REST:** criterio de priorización, los 33 endpoints por módulo, seguridad de acceso por rol.
4. **Documentación de las APIs:** Swagger (captura), referencia, catálogo de errores y Postman.
5. **CI/CD con GitHub Actions:** diagrama del pipeline (los cuatro trabajos), evidencia de ejecuciones verdes, Sonar y Dependabot (§4).
6. **Identificación de vulnerabilidades con OWASP Top Ten:** tabla de §6, método y límites, hallazgos corregidos y plan para los restantes.
7. **Pruebas y calidad:** cifras de §5 y resultados de Postman contra el servicio desplegado.
8. **Riesgos, supuestos y decisiones abiertas:** deuda técnica y preguntas a Calidad.
9. **Conclusiones y plan del Sprint 3** (contenedores y orquestación, observabilidad con Grafana/Prometheus, refinamiento de APIs, aseguramiento mediante tokens).

**Capturas recomendadas (evidencia):** Swagger UI en Render; la ejecución verde del pipeline en GitHub Actions; el panel de SonarCloud (Quality Gate OK); el resultado de Newman/Postman contra Render; la tabla de *workflow runs*; y el diagrama de despliegue.

**Sprint 3 (entregable del curso):** refinamiento de APIs, contenedores y orquestación cuando aplique, observabilidad con Grafana/Prometheus, pruebas de integración y aseguramiento mediante tokens. Ya están adelantados: los `traceId` y los logs JSON (insumo de observabilidad), las pruebas de integración y los tokens JWT con sesión revocable.
