# Plan de trabajo — Sprint 2 (Arquitectura de Software)

Elaborado el 2026-10-07 (Día 1) a partir de: `HUSprint2.md` (export de Azure, 22 HU), `historias-usuario-sprint-2.docx` (notas de planning, reglas de negocio, puntos), `Sprint 2 ArquisuaveBD.docx` (plan de 8 días), los Lineamientos (V2), la Rúbrica y el código actual. Todo lo que dependa de una decisión del equipo está marcado como **decisión** y trae un supuesto por defecto para no bloquear.

Documentos hermanos: [cierre-pendientes-sprint-1.md](cierre-pendientes-sprint-1.md) (MFA, brechas y bugs), [plan-estudio-y-sustentacion.md](plan-estudio-y-sustentacion.md), [azure-boards/](azure-boards/README.md) (tareas importables), [handoff-contexto.md](handoff-contexto.md).

## 1. Objetivo, fechas y cómo se evalúa

**Objetivo:** que un Cliente pueda, de punta a punta, descubrir un negocio, ver un servicio y sus horarios libres, reservar, consultar y cancelar; y que el Proveedor pueda configurar servicios, recursos y horarios y ver sus reservas — sobre la base segura de Sprint 1 (con MFA cerrado) y con calidad medible (Sonar, CI/CD, Swagger, OWASP). Épicas: **1** Proveedores y servicios · **2** Recursos · **3** Reservas.

| Hito | Fecha | Fuente |
|---|---|---|
| Inicio del Sprint 2 | mar 29 sep | Agenda (cronograma) |
| HU listas | 2 oct | notas de planning |
| **Hoy (Día 1)** | **mié 7 oct** | — |
| Meta del equipo para Arquisoft | lun 12 oct (festivo en Colombia) | notas de planning |
| **Corte oficial Arquisoft/BD** | **mar 13 oct** | Agenda: entregables "máximo una semana antes de la review" |
| Tope máximo del equipo | jue 15 oct | notas de planning |
| Pruebas de Calidad | 15 → 20 oct | notas de planning |
| **Review / sustentación** | **mar 20 oct** | Agenda |
| Planning del Sprint 3 | mar 27 oct | Agenda |

> Se planifica contra el **13 de octubre** (la regla del curso es más estricta que el "12 meta / 15 máximo" de las notas). Quedan **6 días** desde hoy. Fecha y formato exacto de la sustentación: **no aparecen en los documentos** — preguntarlo al docente.

**Rúbrica** (peso): Cumplimiento funcional backend **30%** · Arquitectura de solución **25%** (justificar decisiones con criterios técnicos) · Calidad interna **25%** (Sprint 2: cobertura ≥ 65%, deuda ≤ 2 días, complejidad < 50, severidad Minor o superior, 0 vulnerabilidades críticas; 5/5 = sobresaliente, 4/5 = logrado) · Presentación del equipo **20%**. El 30% funcional mide el **% de lo comprometido** (100% = sobresaliente, > 80% = logrado): comprometer menos y entregarlo completo rinde más que comprometer todo y entregar a medias.

**Requisitos de Arquisoft en Sprint 2** (Lineamientos §3.7): diagrama de despliegue · APIs priorizadas · al menos tres APIs REST · CI/CD inicial con GitHub Actions · identificación de vulnerabilidades con OWASP Top Ten · documentación de las APIs (Swagger, §3.1). Además: ADR actualizados en el mismo sprint (con contexto, alternativas, elección, consecuencias, responsable y fecha), diagramas coherentes con el código desplegado, trazabilidad HU → Regla → API → Componente → Tabla → Prueba, retrospectiva con evidencia.

## 2. Alcance recomendado (compromiso) — **decisión del Día 1**

Las notas de planning dejan "definir el alcance" pendiente y proponen dos listas (22 HU = 101 pts; reducida de 15 HU = 70 pts). Propuesta de compromiso por niveles:

| Nivel | HU | Puntos | Esfuerzo nominal | Qué significa |
|---|---|---:|---:|---|
| **Tier 1 — compromiso firme** | HU-08, 09, 13, 14, 18, 19, 20, 22, 23, 24, 25 | 52 | 199 h | Reserva de punta a punta: crear servicio y recurso, asignar, horario, disponibilidad, reservar, ver reservas (cliente y proveedor) y cancelar (cliente) |
| **Tier 2 — alcance de la lista reducida, si el ritmo lo permite** | HU-16, 17, 26, 28 | 18 | 71 h | Desactivar/reactivar recurso, cancelación del proveedor, cancelación al eliminar usuario |
| Tier 3 — fuera del compromiso | HU-07, 10, 11, 12, 15, 21, 27 | 31 | — | Quedan para Sprint 3 (HU-27 explícitamente "junto con Report"; HU-21 se cubre de forma provisional con HU-16/17) |

**Capacidad vs demanda (honesto):** al planificar (2026-10-07) el trabajo sumaba **≈ 479 h nominales** (Dev A 150 · Dev B 180 · Dev C 141 · BD 8): 270 h de las tareas de las HU + 209 h del checklist interno (bugs, MFA, brechas, CI/CD, documentación, BD, estudio). Al implementar aparecieron 4 ítems más (+9 h), así que el total vigente es **488 h**; el generador ([tareas-sprint-2.md](azure-boards/tareas-sprint-2.md)) muestra siempre el total, lo hecho y lo que falta. A Azure suben solo 28 tareas por componente (348 h); los códigos `MFA-nn`, `BUG-n`, `CI-nn`… de este plan son ítems de ese checklist interno. El **mínimo defendible** (Tier 1 + 4 bugs + MFA mínimo + entregables obligatorios de Arquisoft + BD esencial + estudio mínimo) ≈ **320 h**; el 2026-10-07 se cerraron los 4 bugs y el MFA mínimo (≈ 50 h nominales, ver [resultados de pruebas](../resultados-pruebas-sprint-2.md)), así que **quedan ≈ 270 h** de ese mínimo.

| Horas efectivas por persona y día | Capacidad en 7 días × 3 personas | Reducción del esfuerzo real necesaria para caber 320 h |
|---:|---:|---:|
| 3 | 63 h | 5,1× |
| 5 | 105 h | 3,0× |
| 7 | 147 h | 2,2× |

El mínimo solo cabe si el esfuerzo real es ≈ 1/3 del nominal (plantillas, código y pruebas generadas con asistente de IA y **revisadas por una persona de otra pista**). Si el equipo no puede sostener eso, el compromiso debe bajar al **Plan B (núcleo demostrable)**: HU-09, 14, 18, 19, 20, 22, 23 (36 pts: un cliente puede reservar y ver su reserva) y dejar HU-13, 24, 25 como siguiente bloque.

**Reglas de decisión**
- *Checkpoint del Día 4 (sáb 10 oct):* si menos del 60% de las tareas de Tier 1 están en `Closed`, se **congela Tier 2** y todo el equipo estabiliza Tier 1.
- Nada nuevo entra después del Día 6 (feature freeze); Día 7 es estabilización y entrega; Día 8 es amortiguador, evidencias y ensayo.

## 3. Trabajo de arrastre de Sprint 1 y bugs de Calidad

Resumen (detalle, diseño y pruebas en [cierre-pendientes-sprint-1.md](cierre-pendientes-sprint-1.md)):

| Bloque | Qué | Prioridad |
|---|---|---|
| **MFA** | Definir la política (ADR-004) y cerrarla: enrolamiento obligatorio del administrador, login en dos pasos, step-up en operaciones sensibles, límite de intentos, anti-replay, cifrado del secreto, recuperación, pruebas y guía | Alta (mínimo: MFA-01..05, 09..11) |
| **Bugs de Calidad** (GitHub #8–#11) | Límite de registro (6.º intento pasa), validación de longitud, concurrencia 500→409, cuerpo vacío 500→400 | Alta (todos en Día 2) |
| **Brechas de criterios HU-02/04/05/01** | Cambiar contraseña con re-autenticación, mensaje de auto-modificación, doble logout, documentar "el registro no autentica" | Media |
| **Higiene de seguridad** | Rotar la contraseña del admin demo (el repo es **público** y la tiene en `docs/guia-prueba-aplicacion-desplegada.md`); confirmar rotación de la clave de Supabase expuesta en el historial | Alta (Día 1) |
| **Pruebas en CI** | Base común de Testcontainers; `ReservasBackendApplicationTests` depende hoy de un Postgres local y fallaría en CI | Alta |

## 4. Plan día a día (alineado con `Sprint 2 ArquisuaveBD.docx`)

Dev A = Simon (identidad, seguridad, puente con BD) · Dev B = Juan Esteban (contratos, catálogo, disponibilidad, reservas del cliente, consultas) · Dev C = Santiago (recursos, proveedor, CI/CD y despliegue, calidad técnica) · BD = Andraus (+ apoyo de los 3). Reparto tomado de la "distribución propuesta" de `Sprint 1 ArquisuaveBD.docx`; ajustable.

| Día | Tema oficial (Arquisoft / BD) | Dev A | Dev B | Dev C | BD |
|---|---|---|---|---|---|
| **D1 mié 7** | Revisar arquitectura, definir módulos/APIs, revisar bugs · refinar MER · organizar Azure | Cerrar decisiones (§7), **BD-01** (convenciones UUID/ON DELETE), **AZ-01** (importar el lote L01), **SEC-01/02**, ADR-004 borrador, test rojo del bug #8 | Validar contratos de API del Tier 1 · inicio **API-01** (spike Swagger) | **GIT-01** (flujo de ramas), **CI-01** (workflow base), **CI-02** (pedir permisos de Sonar) | MER Sprint 2 (**BD-02**) |
| **D2 jue 8** | APIs de servicios y recursos · modelo físico y tablas | **Bugs #8–#11**; **BD-03** (migración V5 consolidada) | **HU-09** (servicios) | **HU-14** (recursos); CI-01 en verde | DDL y revisión |
| **D3 vie 9** | APIs de disponibilidad · horarios, relaciones e índices | MFA-02/03 (enrolamiento obligatorio, login en dos pasos) · **BD-04** (anti-overbooking) | **HU-18**, **HU-13**; API-01 | **HU-19** (horarios); CI-02/03 (Sonar, JaCoCo) | Índices, tabla de reservas |
| **D4 sáb 10** | **Checkpoint** (§2) | MFA-04/05 (step-up, límite de intentos) · MFA-09 | **HU-20** (disponibilidad) | Preparar HU-24; PERF-01 | Consultas clave |
| **D5 dom 11** | APIs de reservas, cancelaciones, MFA | MFA-10/11 · OWASP-01 | **HU-22** (reservar), **HU-23** | **HU-24**; apoyo de concurrencia en HU-22 | Pruebas de BD |
| **D6 lun 12** (festivo) | Swagger, CI/CD, OWASP · **feature freeze Tier 1** | **HU-25**; si hay ritmo: **HU-28** | Tier 2: **HU-08** · Swagger completo | Tier 2: **HU-16/17/26** | Scripts finales |
| **D7 mar 13** | **Corte oficial.** Pruebas, pipeline, documentación y correcciones | Estabilización, bugs, OWASP-02, evidencias de MFA | DOC-02/06 (arquitectura, trazabilidad), API-02 | DOC-01/03 (despliegue, componentes), CI-04 (triage Sonar) | Pruebas con volumen |
| **D8 mié 14** | Diagramas, documentación final, evidencias · bugs finales · revisión del sprint | EST-X3 (ensayo 2), DOC-05 | DOC-04 (evidencias) | Entrega a Calidad (CI-07 entorno de pruebas) | MER/modelo/scripts finales |
| jue 15 | Tope máximo; arranca Calidad | Atender hallazgos de Calidad | | | |

## 5. Decisiones de arquitectura del Sprint 2 (propuestas → ADR)

Cada una debe quedar como ADR con contexto, alternativas, elección, consecuencias, responsable y fecha (Lineamientos §4), actualizada en este sprint.

1. **Nuevos módulos del monolito modular** (ADR-001/003): `service`, `resource`, `reservation` junto a `provider` e `identity`, cada uno con `controller / application / domain / infrastructure`. Entre módulos solo por interfaces (p. ej. `BusinessAccessService.assertOwner(userId, businessId)` en `provider` para la regla de pertenencia de HU-06). Sin acceso a repositorios ajenos.
2. **Anti-overbooking en la base de datos (ADR-005):** restricción `EXCLUDE USING gist (resource_id WITH =, tstzrange(start_at, end_at) WITH &&) WHERE (status = 'CONFIRMADA')` (extensión `btree_gist`; verificar en Render Postgres 16). La violación (SQLSTATE 23P01) se traduce a `409`. Es la garantía correcta bajo concurrencia; la validación en código solo da mejores mensajes. *Alternativas:* bloqueo pesimista (`SELECT … FOR UPDATE`) o serializable — más frágiles y más lentas.
3. **Retención de historial (ADR-006):** HU-28 exige conservar las reservas pasadas aunque el usuario/servicio/recurso se elimine. Por eso `bookings` guarda *snapshots* (`client_email`, `service_name`, `business_name`, `price_cop`) y sus FK hacia `users`/`services`/`resources` son `ON DELETE SET NULL` (el mismo criterio de Sprint 1: `audit_logs` no tiene FK hacia `users`). El modelo formal de BD debe adoptar esto (ver [conciliación](../conciliacion-modelo-bd-sprint-1.md): `RESTRICT` rompería el borrado de HU-05).
4. **Eliminación de usuario sin acoplar módulos (HU-28):** `identity` publica un evento de dominio síncrono `UserDeletionRequested` dentro de la misma transacción; `reservation` lo escucha y cancela las reservas futuras. `identity` no importa `reservation` (ADR-003).
5. **Patrón de confirmación reutilizable** (HU-11/16/21/28): la primera llamada sin `confirm=true` devuelve `409 CONFIRMATION_REQUIRED` con `affectedBookings: n` y no cambia nada; la segunda con `confirm=true` ejecuta, todo en una transacción.
6. **Estados y motivos** (regla acordada): estado ∈ {`CONFIRMADA`, `CANCELADA`, `COMPLETADA`}; motivo ∈ {`CLIENTE`, `PROVEEDOR`, `ELIMINACION_CUENTA`, `RECURSO_NO_DISPONIBLE`, `SERVICIO_NO_DISPONIBLE`}. Las cancelaciones no decididas por el cliente no están sujetas a la regla de 1 hora.
7. **Tiempo y dinero:** `TIMESTAMPTZ` en UTC; horarios de recurso en hora local `America/Bogota` (sin horario de verano); precio en COP como entero (pesos), `0` = gratuito, **snapshot** en la reserva (HU-10/22).
8. **Convenciones de API:** `/api/v1/…`, UUID, paginación con tope (tamaño por defecto 20, máximo 100), errores en el formato de `ApiError`, códigos nuevos: `CONFIRMATION_REQUIRED` (409), `MFA_REQUIRED` (401), `MFA_ENROLLMENT_REQUIRED` (403).
9. **Política de MFA (ADR-004):** ver [cierre-pendientes-sprint-1.md §2](cierre-pendientes-sprint-1.md). Respaldo normativo: Lineamientos §3.4 y §6.2 ("exigir MFA para accesos administrativos o sensibles").
10. **Migraciones:** una sola persona (Dev A, puente backend↔BD) numera y mergea las migraciones Flyway; V5 consolidada el Día 2; después solo incrementales. Evita colisiones de versión entre 3 desarrolladores en paralelo.

## 6. Cumplimiento de los requisitos del curso

| Requisito (Lineamientos) | Cómo se cumple | Tareas |
|---|---|---|
| ≥ 3 APIs REST | Identidad (`/auth`, `/users`), Proveedor (`/providers`, `/businesses`), Catálogo y recursos (`/services`, `/resources`), Reservas (`/bookings`) | HU del Tier 1 |
| Documentación de APIs (Swagger/OpenAPI versionado) | springdoc 3.1.x con esquema Bearer; spike de compatibilidad con Spring Boot 4/Jackson 3 (timebox 2 h) | API-01, API-02 |
| CI/CD inicial con GitHub Actions | Build + pruebas + JaCoCo + Sonar + build de imagen; Render ya despliega desde `main` | CI-01..07, GIT-01 |
| OWASP Top Ten | Matriz A01–A10 (§8) con hallazgos y correcciones | OWASP-01/02 |
| Diagrama de despliegue | Cliente, backend Spring Boot (contenedor), PostgreSQL, Render, GitHub Actions, Sonar | DOC-01 |
| Calidad (Sonar + Quality Gate) | Cobertura ≥ 65%, deuda ≤ 2 días, complejidad < 50, severidad Minor+, 0 críticas | CI-02..04 |
| ADR, diagramas y matriz coherentes con el código | ADR-004/005/006, diagramas Sprint 2, trazabilidad generada | DOC-02/03/06 |
| Gestión: sprint backlog, métricas en Azure, retrospectiva | Lotes importados, burndown, 2 métricas, acta de retro | AZ-01, EST-X4 |
| RNF base: 200 solicitudes/min, respuesta ≤ 30 s | Prueba de carga básica | PERF-01 |

## 7. Decisiones abiertas y supuesto por defecto (cerrar el Día 1)

| # | Tema | Pregunta | Supuesto por defecto | Decide |
|---|---|---|---|---|
| 1 | Alcance | ¿22 HU, 15 o por niveles? | Tier 1 firme + Tier 2 como estiramiento (§2) | Equipo + Calidad |
| 2 | HU-22 | El AC dice "mismo servicio en el mismo horario", pero el modelo tiene recursos | Anti-overbooking **por recurso**; un servicio con 2 recursos admite 2 reservas simultáneas | Equipo |
| 3 | HU-26/28 | Los AC usan estados compuestos ("Cancelada por proveedor") | Estado + motivo (regla acordada) | Equipo |
| 4 | HU-28 | Incluye eliminar la **propia** cuenta (cliente/proveedor) | Solo Administrador en Sprint 2; autoeliminación al backlog | Equipo + Calidad |
| 5 | Notificaciones | Los AC de HU-21/25/26/28 piden "notificar" pero están **fuera de alcance** | Se registra un evento de auditoría; sin correo/push | Calidad |
| 6 | HU-13 vs HU-20 | El catálogo exige sesión; la disponibilidad se describía como pública | **Decidido el 2026-10-08 (Simon):** el catálogo (HU-13) exige sesión; la disponibilidad (HU-20) es **pública**, por el escenario "usuario sin sesión" de su AC (CP-HU20-09) | Decidido |
| 7 | HU-14 | Tipos de recurso: "sala, equipo, personal" vs "consultorio, cancha, puesto" | Enum `SALA`, `EQUIPO`, `PERSONAL` | Equipo |
| 8 | HU-08 | Unidad y tope de la antelación | Horas enteras, mínimo 1, máximo 720 | Equipo |
| 9 | HU-22 | El AC pide que el cliente envíe hora de fin | El cliente envía solo el inicio; fin = inicio + duración (si envía fin, se valida) | Equipo |
| 10 | HU-09/13 | HU-13 muestra "descripción" pero HU-09 no la captura | Campo opcional `description` en HU-09 | Equipo |
| 11 | HU-20 | Granularidad de los horarios | Inicios cada 30 min dentro del rango, si cabe la duración | Equipo |
| 12 | HU-26 | ¿Aplica la regla de 1 hora a la cancelación del proveedor? | No aplica | Calidad |
| 13 | Errores | Lineamientos §3.3 piden `errorCode/details/traceId`; el formato vigente es otro | Mantener `ApiError` y agregar `traceId` | Docente/Calidad |
| 14 | Git | Lineamientos §7.1: trunk-based; el repo tiene `main` + `dev` | `main` protegida, ramas `feature/<HU>-<desc>`, PR a `main`; `dev` se retira o espeja el entorno de pruebas | Santiago + equipo |
| 15 | Sustentación | Fecha y formato | Review del 20 oct; formato por confirmar | Docente |

## 8. OWASP Top 10 — estado y brechas (base de OWASP-01)

| Riesgo | Estado actual (verificado en código) | Brecha | Tarea |
|---|---|---|---|
| A01 Control de acceso | Pertenencia (HU-06), `@PreAuthorize`, 401/403 uniformes | IDOR en negocios, servicios, recursos y reservas nuevos | `HU*-T4` + matriz de autorización por endpoint |
| A02 Fallas criptográficas | BCrypt, JWT HS256 de 1 h, HTTPS en Render | Secreto MFA en texto plano; un solo `JWT_SECRET` | MFA-07 |
| A03 Inyección | JPA parametrizado, Bean Validation | Sin límites de longitud (bug #9); consultas nativas nuevas deben parametrizarse | BUG-9 + revisión de PR |
| A04 Diseño inseguro | Límite solo en registro (y con bug) | Login/MFA sin límite; anti-overbooking | BUG-8, MFA-05, BD-04 |
| A05 Configuración insegura | Actuator solo `health,info`; errores sin stack | Swagger en producción, CORS no definido, cabeceras, ruido de "generated password", perfil `dev` por defecto | OWASP-02, SEC-03, API-01 |
| A06 Componentes vulnerables | Spring Boot 4.1.1, Testcontainers actualizado | Sin escaneo automático | CI-06 |
| A07 Autenticación | Política de contraseña, sesiones revocables, MFA parcial | Fuerza bruta, MFA no obligatorio, sin cambio de contraseña | MFA-02/05, SP1-01 |
| A08 Integridad | Despliegue desde `main` | Sin protección de ramas ni CI | CI-01, CI-05 |
| A09 Registro y monitoreo | Auditoría sin secretos | Sin `traceId` ni logs JSON | API-03 |
| A10 SSRF | Sin llamadas salientes | No aplica (documentarlo) | — |

## 9. Calidad: línea base y estrategia

Línea base medida hoy con JaCoCo (101 pruebas unitarias, 0 fallas): **77,1% de líneas**, 77,9% de instrucciones, 76,4% de ramas. Puntos bajos: `RegistrationRateLimiter` 10%, `GlobalExceptionHandler` 40%, `RestAccessDeniedHandler`/`RestAuthenticationEntryPoint` 0%, `AuditServiceImpl` 0%, controladores 0–30% (los cubren las pruebas de integración, que corren en CI con Docker). Reglas: todo código nuevo entrega pruebas unitarias (AAA, Mockito) con ≥ 65% y las reglas de negocio al 100%; los flujos van además con prueba de integración (Testcontainers) y requests en la colección de Postman. Deuda, complejidad y vulnerabilidades: **sin línea base hasta habilitar Sonar** (CI-02) — es la primera dependencia externa del sprint (permisos de organización; Calidad pidió acceso u ownership).

## 10. Flujo de trabajo (Git + Azure + Definición de hecho)

- **Ramas:** `feature/HU09-crear-servicio`, `fix/BUG-8-limite-registro`, `chore/…`; vida corta (días), actualizar contra `main` antes de integrar, sin commits directos a la rama protegida (Lineamientos §7.1).
- **PR:** título `[HU09-T2] …`; descripción con el checklist de DoD, `Fixes #n` si cierra un issue de Calidad y los códigos de tarea; **revisor de otra pista** (alimenta el plan de estudio). Con la integración *Azure Boards ↔ GitHub* instalada, `AB#<id>` enlaza la tarea.
- **Azure (política del 2026-10-07):** pocas tareas, **una por componente o parte desarrollada** (`HU09-DATOS`, `HU09-API`, `SEG-01`, `ARQ-03`…), cada una con responsable y estimación (Lineamientos §3.6); estados `New → Active → Closed`. Las correcciones, bugs, pruebas, documentación suelta, estudio e higiene **no** generan tarea (los bugs van como `Fixes #n`). Solo un componente nuevo no cubierto agrega una `chg(...)` en [backlog_data.py](azure-boards/backlog_data.py) y un lote CSV nuevo (ver [azure-boards/README.md](azure-boards/README.md)).
- **Definición de hecho** (Lineamientos §9.1): HU aceptada contra criterios y reglas; código revisado e integrado con pipeline exitoso; Quality Gate y controles de seguridad cumplidos; documentación, contrato de API, diagramas y migraciones actualizados; despliegue comprobado con logs; sin defectos ni vulnerabilidades críticas abiertas.
- **Cierre de un requisito:** criterio → casos de prueba → implementación → pruebas en verde en CI → revisión cruzada → verificación en el entorno de pruebas → evidencia → Azure/GitHub actualizados → validación de Calidad.

## 11. Riesgos

| # | Riesgo | Nivel | Mitigación |
|---|---|---|---|
| R1 | Capacidad menor que la demanda (§2) | Alto | Niveles, checkpoint del Día 4, plantillas y asistente de IA con revisión cruzada |
| R2 | BD diseña tablas con BIGSERIAL/`RESTRICT` y rompe HU-05/HU-28 | Alto | **BD-01 bloqueante el Día 1** (convenciones en la conciliación) |
| R3 | Sonar sin permisos (25% de la nota) | Alto | CI-02 el Día 1; plan B: SonarQube en contenedor o escaneo desde una cuenta con permisos |
| R4 | Credenciales demo en un repo público | Alto | SEC-01/02 el Día 1; MFA obligatorio para admin reduce el impacto |
| R5 | Conocimiento concentrado en una persona (sustentación) | Alto | Plan de estudio, revisión cruzada, ensayos |
| R6 | springdoc 3.x + Boot 4/Jackson 3 incompatible | Medio | Spike de 2 h; plan B: contrato OpenAPI escrito a mano y publicado |
| R7 | Ambigüedad de HU (§7) | Medio | Supuestos por defecto el Día 1 |
| R8 | Testcontainers no corre en Docker Desktop local | Medio | Correr las IT en CI; probar el bump 1.21.3 con Docker encendido |
| R9 | Render free: cold start ≈ 2 min; la base gratuita expira a los 90 días (≈ 21 dic) | Medio | Calentar el servicio antes de demos/QA; decidir plan antes de esa fecha |
| R10 | Colisión de migraciones y de merges | Medio | Un solo dueño de migraciones, módulos separados, ramas cortas |
| R11 | Concurrencia en reservas | Medio | `EXCLUDE` + prueba concurrente |

## 12. Entregables al corte (13 oct)

- [ ] Tier 1 funcionando y desplegado en el entorno de pruebas; Tier 2 solo si cerró completo.
- [ ] MFA cerrado según ADR-004 con evidencia; 4 bugs de Calidad corregidos y verificados (issues #8–#11 cerrados por Calidad).
- [ ] Swagger publicado y probado con "Authorize"; colección de Postman compartida.
- [ ] Pipeline verde (build, pruebas, JaCoCo, Sonar); Quality Gate del Sprint 2 en verde o con plan documentado.
- [ ] OWASP-01 con hallazgos corregidos o aceptados por escrito.
- [ ] Diagrama de despliegue y diagramas de componentes/paquetes del Sprint 2; ADR-004/005/006; matriz de trazabilidad actualizada.
- [ ] `docs/resultados-pruebas-sprint-2.md` y `docs/estado-proyecto-sprint-2.md`; evidencias en `docs/sprint-2/evidencias/`.
- [ ] Azure: lote L01 importado (28 tareas por componente), con responsable y estimación, estados al día.
- [ ] Credenciales demo rotadas y fuera del repo; entorno de pruebas para Calidad con datos semilla.
- [ ] Ensayo de sustentación n.º 2 hecho (ver el plan de estudio).
