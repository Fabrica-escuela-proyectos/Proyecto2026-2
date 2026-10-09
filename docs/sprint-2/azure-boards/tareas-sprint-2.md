# Tareas de Azure del Sprint 2 — por componente

> **Generado automáticamente** por `generar.py` desde `backlog_data.py`. No editar a mano: edita los datos y vuelve a correr `python docs/sprint-2/azure-boards/generar.py`. Proceso Agile de Azure (User Story / Task).

**Política (2026-10-07):** Azure lleva pocas tareas, de nivel arquitectónico: una por componente o parte desarrollada de la aplicación. Las correcciones, bugs, pruebas, documentación suelta, estudio e higiene **no** generan tarea (ver `README.md`); su detalle vive en el checklist interno de la última sección.

## Resumen

| Concepto | Tareas | Horas |
|---|---:|---:|
| Azure · tareas de las HU del sprint | 20 | 270 |
| Azure · tareas técnicas (3 historias técnicas) | 8 | 78 |
| **Total que se sube a Azure** | **28** | **348** |
| Checklist interno (NO se sube; 22 de sus 64 ítems quedan agrupados en las tareas técnicas) | 64 | 218 |
| **Trabajo nominal total** (HU + checklist interno; base de la capacidad del plan) | | **488** |
| &nbsp;&nbsp;↳ ya hecho del checklist (14 ítems, 3 parciales sin contar) | | 58 |
| &nbsp;&nbsp;↳ **por hacer** (HU + checklist pendiente) | | **430** |

## Lotes (un CSV por lote; un lote ya subido NO se vuelve a importar)

| Lote | Archivo | Estado | Historias | Tareas | Horas |
|---|---|---|---:|---:|---:|
| L01 | `lotes/azure-import_L01_tareas-por-componente.csv` | subido | 18 | 28 | 348 |

## Esfuerzo nominal por responsable (HU + checklist interno)

| Responsable | Planificado (h) | Por hacer (h) |
|---|---:|---:|
| Dev A (Simon Betancur) · Identidad, seguridad y calidad | 159 | 101 |
| Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente | 180 | 180 |
| Dev C (Santiago Rendón) · Recursos, horarios, cancelaciones, CI/CD y despliegue | 141 | 141 |
| Bases de Datos (Juan Sebastián Andraus, con apoyo de los 3 de Arquisoft) | 8 | 8 |

Tareas de HU: Tier 1 = 200 h, Tier 2 = 70 h (estimación nominal, sin asistente de IA).

## L01 · tareas-por-componente (subido)

### HU 09 - Crear servicio — ID Azure 74

5 pts · Tier 1 · Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · depende de: HU-03

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU09-DATOS | Módulo de servicios: modelo de datos y migración Flyway (tabla services) | Development | New | 3 | 1 | B |
| HU09-API | Módulo de servicios: crear y listar los servicios del negocio (reglas de negocio y API REST) | Development | New | 16 | 1 | B |

### HU 13 - Consultar negocios y servicios — ID Azure 78

3 pts · Tier 1 · Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · depende de: HU-09

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU13-API | Catálogo de negocios y servicios: consulta paginada de negocios con sus servicios activos (reglas de negocio y API REST) | Development | New | 13 | 1 | B |

### HU 14 - Registrar recurso — ID Azure 79

5 pts · Tier 1 · Dev C (Santiago Rendón) · Recursos, horarios, cancelaciones, CI/CD y despliegue · depende de: HU-03

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU14-DATOS | Módulo de recursos: modelo de datos y migración Flyway (tabla resources) | Development | New | 3 | 1 | C |
| HU14-API | Módulo de recursos: registrar y listar los recursos del negocio (reglas de negocio y API REST) | Development | New | 16 | 1 | C |

### HU 18 - Asignar recursos a un servicio — ID Azure 83

5 pts · Tier 1 · Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · depende de: HU-09, HU-14

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU18-DATOS | Asignación de recursos a servicios: modelo de datos y migración Flyway (tabla service_resources) | Development | New | 3 | 1 | B |
| HU18-API | Asignación de recursos a servicios: asignar y consultar los recursos de un servicio (reglas de negocio y API REST) | Development | New | 16 | 1 | B |

### HU 19 - Definir horarios de atención de un recurso — ID Azure 84

3 pts · Tier 1 · Dev C (Santiago Rendón) · Recursos, horarios, cancelaciones, CI/CD y despliegue · depende de: HU-14

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU19-DATOS | Horarios de atención de recursos: modelo de datos y migración Flyway (tabla resource_availability) | Development | New | 2 | 1 | C |
| HU19-API | Horarios de atención de recursos: definir y consultar la disponibilidad semanal de un recurso (reglas de negocio y API REST) | Development | New | 11 | 1 | C |

### HU 20 - Consultar disponibilidad de un servicio — ID Azure 85

5 pts · Tier 1 · Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · depende de: HU-18, HU-19

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU20-API | Motor de disponibilidad: consulta pública de los horarios libres de un servicio por fecha (reglas de negocio y API REST) | Development | New | 19 | 1 | B |

### HU 22 - Crear reserva — ID Azure 87

8 pts · Tier 1 · Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · depende de: HU-08, HU-09, HU-14, HU-18, HU-19, HU-20

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU22-DATOS | Módulo de reservas: modelo de datos y migración Flyway (tabla bookings) | Development | New | 4 | 1 | B |
| HU22-API | Módulo de reservas: crear reserva con anti-overbooking (reglas de negocio y API REST) | Development | New | 24 | 1 | B |

### HU 23 - Consultar mis reservas — ID Azure 88

5 pts · Tier 1 · Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · depende de: HU-22

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU23-API | Módulo de reservas: consulta de las reservas del cliente (reglas de negocio y API REST) | Development | New | 19 | 1 | B |

### HU 24 - Consultar reservas del negocio — ID Azure 89

5 pts · Tier 1 · Dev C (Santiago Rendón) · Recursos, horarios, cancelaciones, CI/CD y despliegue · depende de: HU-22

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU24-API | Módulo de reservas: consulta de las reservas del negocio (reglas de negocio y API REST) | Development | New | 19 | 1 | C |

### HU 25 - Cancelar reserva como cliente — ID Azure 90

5 pts · Tier 1 · Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · depende de: HU-23

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU25-API | Módulo de reservas: cancelación de la reserva por el cliente (reglas de negocio y API REST) | Development | New | 19 | 1 | B |

### HU 08 - Definir antelación mínima de reserva — ID Azure 73

3 pts · Tier 1 · Dev B (Juan Esteban González) · Catálogo, disponibilidad y reservas del cliente · depende de: HU-03

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU08-API | Configuración del negocio: antelación mínima de reserva por negocio (reglas de negocio y API REST) | Development | New | 13 | 1 | B |

### HU 16 - Desactivar recurso — ID Azure 81

5 pts · Tier 2 · Dev C (Santiago Rendón) · Recursos, horarios, cancelaciones, CI/CD y despliegue · depende de: HU-14, HU-22, HU-24

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU16-API | Módulo de recursos: desactivar un recurso con confirmación y cancelación de reservas futuras (reglas de negocio y API REST) | Development | New | 19 | 2 | C |

### HU 17 - Reactivar recurso — ID Azure 82

3 pts · Tier 2 · Dev C (Santiago Rendón) · Recursos, horarios, cancelaciones, CI/CD y despliegue · depende de: HU-16

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU17-API | Módulo de recursos: reactivar un recurso (reglas de negocio y API REST) | Development | New | 13 | 2 | C |

### HU 26 - Cancelar reserva como proveedor — ID Azure 91

5 pts · Tier 2 · Dev C (Santiago Rendón) · Recursos, horarios, cancelaciones, CI/CD y despliegue · depende de: HU-24

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU26-API | Módulo de reservas: cancelación de la reserva por el proveedor (reglas de negocio y API REST) | Development | New | 19 | 2 | C |

### HU 28 - Cancelar reservas futuras al eliminar un usuario — ID Azure 92

5 pts · Tier 2 · Dev A (Simon Betancur) · Identidad, seguridad y calidad · depende de: HU-05, HU-22, HU-26

| Código | Tarea | Actividad | Estado | h | Prio | Resp. |
|---|---|---|---|---:|---:|---|
| HU28-API | Identidad y reservas: cancelación de reservas futuras al eliminar un usuario (reglas de negocio y API REST) | Development | New | 19 | 2 | A |

### TECH-01 · Seguridad de acceso: MFA y control de intentos

Cierra el MFA que quedó a medias en el Sprint 1 y protege registro, login y MFA contra fuerza bruta (OWASP A07). Detalle: docs/sprint-2/cierre-pendientes-sprint-1.md

| Código | Tarea | Actividad | Estado | h | Prio | Resp. | Avance / estado sugerido en Azure |
|---|---|---|---|---:|---:|---|---|
| SEG-01 | Autenticación multifactor (TOTP) obligatoria para administradores: enrolamiento forzoso, login en dos pasos y confirmación en operaciones sensibles | Development | New | 23 | 1 | A | Active (6/7 ítems hechos, 1 parcial) |
| SEG-02 | Control de intentos reutilizable para registro, login y MFA (429 por origen) | Development | New | 5 | 1 | A | Closed (tras la revisión del PR) |

### TECH-02 · Plataforma: CI/CD y calidad

Pipeline en GitHub Actions y análisis estático (SonarCloud) con los Quality Gates del Sprint 2: cobertura >= 65%, deuda <= 2 días, complejidad < 50, severidad Minor+, 0 vulnerabilidades críticas.

| Código | Tarea | Actividad | Estado | h | Prio | Resp. | Avance / estado sugerido en Azure |
|---|---|---|---|---:|---:|---|---|
| PLT-01 | Pipeline CI/CD con GitHub Actions: build, pruebas, cobertura (JaCoCo), imagen Docker y entorno de pruebas desplegado | Deployment | New | 8 | 1 | C | New |
| PLT-02 | Análisis estático de calidad (SonarCloud) con los Quality Gates del Sprint 2 | Deployment | New | 7 | 1 | C | New |

### TECH-03 · Arquitectura, API y seguridad del Sprint 2

Documentación interactiva de la API (Swagger), modelo de datos coordinado con BD, diagramas y ADRs, y revisión OWASP Top 10: los entregables de Arquitectura de Software del Sprint 2.

| Código | Tarea | Actividad | Estado | h | Prio | Resp. | Avance / estado sugerido en Azure |
|---|---|---|---|---:|---:|---|---|
| ARQ-01 | Documentación interactiva de la API (Swagger/OpenAPI) | Development | New | 7 | 1 | B | New |
| ARQ-02 | Modelo de datos del Sprint 2: convenciones y migraciones Flyway coordinadas con BD | Design | New | 7 | 1 | A | Active (0/2 ítems hechos, 1 parcial) |
| ARQ-03 | Arquitectura del Sprint 2: diagrama de despliegue, diagrama de componentes y ADRs | Design | New | 12 | 1 | C | New |
| ARQ-04 | Seguridad OWASP Top 10: revisión del código y endurecimiento (cabeceras, CORS, límites de entrada) | Development | New | 9 | 1 | A | New |

## Checklist interno — pendientes de detalle (NO se suben a Azure)

Códigos que citan los demás documentos del sprint. La columna *Azure* indica la tarea técnica que agrupa el ítem; `—` = se hace sin tarea propia (corrección, higiene, documentación, estudio o extensión opcional). La columna *Avance* (✔ hecho, ◐ parcial) se actualiza en `AVANCE` (`backlog_data.py`).

| Código | Área | Pendiente | Actividad | h | Prio | Resp. | Azure | Avance |
|---|---|---|---|---:|---:|---|---|---|
| BUG-8 | Bugs de Calidad | [#8] El 6.º intento de registro supera el límite y crea la cuenta (HU-01) | Development | 6 | 1 | A | — | ✔ 2026-10-07 · AttemptLimiter atómico (el 6.º intento se rechaza). Regresión: AttemptLimiterTest, RateLimitersTest, RegistrationRateLimitIntegrationTest |
| BUG-9 | Bugs de Calidad | [#9] Sin validación de longitud en nombre, correo y contraseña (HU-01/02/03) | Development | 5 | 2 | A | — | ✔ 2026-10-07 · @Size en los DTO y tope de 72 bytes. Regresión: RequestLengthValidationTest, ApiErrorHandlingIntegrationTest |
| BUG-10 | Bugs de Calidad | [#10] Registro concurrente con el mismo correo devuelve 500 en lugar de 409 (HU-01) | Development | 7 | 1 | A | — | ✔ 2026-10-07 · DataIntegrityViolationException -> 409/400. Regresión: RegistrationConcurrencyIntegrationTest (sin el manejador devolvía [500, 201]) |
| BUG-11 | Bugs de Calidad | [#11] Cuerpo vacío en registro y login devuelve 500 en lugar de 400 (HU-01/02/03) | Development | 6 | 1 | A | — | ✔ 2026-10-07 · manejadores 400/404/405/415/406. Regresión: GlobalExceptionHandlerTest, ApiErrorHandlingIntegrationTest (sin ellos, 12 de 19 daban 500) |
| MFA-01 | MFA | ADR-004: política de MFA (quién, enrolamiento obligatorio, step-up, recuperación) | Documentation | 2 | 1 | A | SEG-01 | ✔ 2026-10-07 · ADR-004 redactado; estado Propuesto: falta la aprobación de los tres de Arquisoft |
| MFA-02 | MFA | Enrolamiento obligatorio de ADMIN: sin MFA activo solo puede usar /auth/mfa/* y logout (403 MFA_ENROLLMENT_REQUIRED) | Development | 5 | 1 | A | SEG-01 | ✔ 2026-10-07 · MfaEnrollmentFilter (403 MFA_ENROLLMENT_REQUIRED) |
| MFA-03 | MFA | Login en dos pasos: 401 MFA_REQUIRED cuando la contraseña es válida y falta el código | Development | 2 | 1 | A | SEG-01 | ✔ 2026-10-07 · login en dos pasos (401 MFA_REQUIRED) |
| MFA-04 | MFA | Step-up MFA (header X-MFA-Code) en PATCH /users/{id}/role y DELETE /users/{id} | Development | 4 | 1 | A | SEG-01 | ✔ 2026-10-07 · StepUpService + header X-MFA-Code en PATCH role y DELETE user |
| MFA-05 | MFA | Límite de intentos en login y verificación MFA (429, por IP+correo) | Development | 5 | 1 | A | SEG-02 | ✔ 2026-10-07 · AuthAttemptLimiter para login y confirmación (429 tras 5 fallos) |
| MFA-06 | MFA | Anti-replay TOTP: guardar last_used_step (migración V5) y rechazar códigos ya usados | Development | 3 | 2 | A | — |  |
| MFA-07 | MFA | Cifrar el secreto MFA en reposo (AES-256-GCM, clave MFA_ENCRYPTION_KEY por entorno) | Development | 4 | 2 | A | — |  |
| MFA-08 | MFA | Reset de MFA por otro administrador (DELETE /users/{id}/mfa) + runbook de recuperación | Development | 4 | 3 | A | — |  |
| MFA-09 | MFA | Pruebas unitarias MFA: vectores RFC 6238, ventana ±1, replay, step-up, estados del enrolamiento | Testing | 4 | 1 | A | SEG-01 | ✔ 2026-10-07 · TotpServiceTest con vectores RFC 6238, AuthServiceImplTest, StepUpServiceImplTest, MfaEnrollmentFilterTest |
| MFA-10 | MFA | Prueba de integración del flujo MFA completo (setup→activate→login con código→step-up) con Testcontainers | Testing | 4 | 1 | A | SEG-01 | ✔ 2026-10-07 · MfaFlowIntegrationTest contra PostgreSQL real (14 casos) |
| MFA-11 | MFA | Prueba manual E2E con app autenticadora real + colección Postman + evidencia | Testing | 2 | 1 | A | SEG-01 | ◐ 2026-10-07 · verificación E2E local 28/28 contra la app real con el script de TOTP; falta app autenticadora real, colección Postman y evidencia en docs/sprint-2/evidencias/ |
| MFA-12 | MFA | Guía operativa 'Configurar MFA' (administrador) y guía para QA (calcular el código TOTP) | Documentation | 2 | 2 | A | — | ◐ 2026-10-07 · guía de prueba desplegada actualizada con el flujo de MFA; falta la guía operativa de recuperación |
| SP1-01 | Brechas de Sprint 1 | HU-02: cambiar la propia contraseña con re-autenticación (PUT /users/me/password), revocar otras sesiones, auditar | Development | 8 | 2 | A | — |  |
| SP1-02 | Brechas de Sprint 1 | HU-05: mensaje de auto-modificación para cualquier rol (evaluar auto-modificación antes del chequeo de rol) | Development | 2 | 3 | A | — |  |
| SP1-03 | Brechas de Sprint 1 | HU-04: definir y probar el comportamiento de doble logout / logout sin sesión (mensaje claro) | Development | 2 | 3 | A | — |  |
| SP1-04 | Brechas de Sprint 1 | HU-01: documentar la decisión 'el registro no autentica' (nota en endpoints/ADR) | Documentation | 1 | 3 | A | — |  |
| SP1-05 | Brechas de Sprint 1 | Base común de pruebas de integración con Testcontainers (incluye ReservasBackendApplicationTests) lista para CI | Development | 3 | 1 | A | — | ✔ 2026-10-07 · AbstractIntegrationTest (contenedor único); ReservasBackendApplicationTests ya no depende de localhost:5432 |
| SP1-06 | Brechas de Sprint 1 | Limpiar referencias a documentos inexistentes (HU-01-checklist, matriz-actualizaciones) y el Javadoc desactualizado de IdentityServiceImpl | Development | 1 | 4 | A | — |  |
| SEC-01 | Higiene de seguridad | Rotar la contraseña del admin demo y quitar credenciales de docs públicos (el repo es público) | Deployment | 1 | 1 | A | — |  |
| SEC-02 | Higiene de seguridad | Confirmar rotación de la contraseña de Supabase expuesta en el historial (commit 5ae30bd) | Deployment | 1 | 1 | A | — |  |
| SEC-03 | Higiene de seguridad | Quitar el 'Using generated security password' (UserDetailsService vacío o excluir la autoconfiguración) | Development | 1 | 3 | A | — |  |
| CI-01 | Plataforma (CI/CD y calidad) | Workflow GitHub Actions: build + test + JaCoCo + build de imagen Docker (en .github/workflows/ci.yml) | Deployment | 4 | 1 | C | PLT-01 |  |
| CI-02 | Plataforma (CI/CD y calidad) | Habilitar SonarCloud/SonarQube con los gates del Sprint 2 (requiere admin/owner de la organización GitHub) | Deployment | 3 | 1 | C | PLT-02 |  |
| CI-03 | Plataforma (CI/CD y calidad) | Ajustar JaCoCo (exclusiones, reporte XML para Sonar) y verificar cobertura >= 65% | Development | 2 | 1 | C | PLT-01 |  |
| CI-04 | Plataforma (CI/CD y calidad) | Triage de hallazgos iniciales de Sonar: deuda <= 2 días, complejidad < 50, 0 vulnerabilidades críticas | Development | 4 | 1 | C | PLT-02 |  |
| CI-05 | Plataforma (CI/CD y calidad) | Protección de ramas main/dev (PR obligatorio + CI verde + 1 revisión) | Deployment | 1 | 2 | C | — |  |
| CI-06 | Plataforma (CI/CD y calidad) | Dependabot / OWASP Dependency-Check en CI (job semanal o por PR) | Development | 2 | 3 | C | — |  |
| CI-07 | Plataforma (CI/CD y calidad) | Entorno de pruebas para Calidad (Render) con datos semilla y credenciales fuera del repo | Deployment | 2 | 1 | C | PLT-01 |  |
| API-01 | API, seguridad y documentación | Swagger/OpenAPI con springdoc 3.1.x: spike de compatibilidad con Spring Boot 4.1/Jackson 3, esquema Bearer, ejemplos y errores | Development | 5 | 1 | B | ARQ-01 |  |
| API-02 | API, seguridad y documentación | Documentar contratos y códigos de error del Sprint 2 (errores-api-sprint-2.md + ejemplos en Swagger) | Documentation | 2 | 2 | B | ARQ-01 |  |
| OWASP-01 | API, seguridad y documentación | Revisión OWASP Top 10 (A01–A10): matriz contra el código, hallazgos y correcciones | Development | 6 | 1 | A | ARQ-04 |  |
| OWASP-02 | API, seguridad y documentación | Cabeceras de seguridad, CORS explícito, límites de tamaño de payload y de paginación | Development | 3 | 2 | A | ARQ-04 |  |
| DOC-01 | API, seguridad y documentación | Diagrama de despliegue (cliente, backend Spring Boot, PostgreSQL, Render, GitHub Actions, Sonar) | Design | 3 | 1 | C | ARQ-03 |  |
| DOC-02 | API, seguridad y documentación | Actualizar arquitectura: módulos service/resource/reservation y ADR-004 (MFA), ADR-005 (anti-overbooking), ADR-006 (retención de historial) | Documentation | 5 | 1 | B | ARQ-03 |  |
| DOC-03 | API, seguridad y documentación | Diagramas de componentes y paquetes del Sprint 2 con tabla de conexiones | Design | 4 | 2 | C | ARQ-03 |  |
| DOC-04 | API, seguridad y documentación | Evidencias del sprint: reportes Sonar/JaCoCo, pipeline verde, Postman, capturas | Documentation | 3 | 2 | B | — |  |
| DOC-05 | API, seguridad y documentación | Actualizar resultados de pruebas y estado del proyecto (Sprint 2) | Documentation | 2 | 2 | B | — |  |
| BD-01 | Modelo de datos | Acordar convenciones (UUID, TIMESTAMPTZ, ON DELETE, nombres) y alinear el modelo formal con el esquema Flyway real | Design | 3 | 1 | A | ARQ-02 | ◐ 2026-10-08 · docs/bd/convenciones-bd.md redactado como propuesta; falta acordarlo con BD (Andraus) y alinear el modelo formal |
| BD-02 | Modelo de datos | MER y entidades Sprint 2: services, resources, service_resources, resource_availability, bookings (+ columnas de businesses) | Design | 4 | 1 | BD | — |  |
| BD-03 | Modelo de datos | Modelo físico y DDL propuesto → migraciones Flyway V5+ revisadas por Arquisoft | Development | 4 | 1 | A | ARQ-02 |  |
| BD-04 | Modelo de datos | Restricción anti-overbooking (EXCLUDE USING gist, btree_gist) + índices de disponibilidad + prueba de concurrencia | Development | 4 | 1 | A | — |  |
| BD-05 | Modelo de datos | Consultas clave del Sprint 2 (disponibilidad, reservas por cliente/negocio, cancelaciones masivas) con EXPLAIN | Design | 4 | 2 | B | — |  |
| BD-06 | Modelo de datos | Scripts finales y pruebas con volumen (datos sintéticos) | Testing | 4 | 2 | BD | — |  |
| BD-07 | Modelo de datos | Seguridad en BD: rol de aplicación con privilegios mínimos, sin DROP/TRUNCATE, secretos fuera del repo | Development | 2 | 3 | C | — |  |
| EST-A1 | Estudio y sustentación | Estudio dirigido (Dev A): identidad, JWT/sesiones, MFA, OWASP — lectura + traza de petición + hoja resumen | Requirements | 8 | 1 | A | — |  |
| EST-B1 | Estudio y sustentación | Estudio dirigido (Dev B): catálogo, disponibilidad y reservas — reglas de negocio, flujo y anti-overbooking | Requirements | 8 | 1 | B | — |  |
| EST-C1 | Estudio y sustentación | Estudio dirigido (Dev C): arquitectura, BD, CI/CD, despliegue y calidad | Requirements | 8 | 1 | C | — |  |
| EST-X1 | Estudio y sustentación | Revisión cruzada de PR: cada integrante revisa PR de una pista distinta (continuo durante el sprint) | Development | 6 | 2 | A | — |  |
| EST-X2 | Estudio y sustentación | Ensayo de sustentación n.º 1 (con banco de preguntas) | Requirements | 3 | 1 | A | — |  |
| EST-X3 | Estudio y sustentación | Ensayo de sustentación n.º 2 + demo en vivo contra el entorno desplegado | Requirements | 3 | 1 | A | — |  |
| EST-X4 | Estudio y sustentación | Retrospectiva del Sprint 2 con evidencia (acta/captura) — exigida en Gestión (Lineamientos §3.7) | Requirements | 1 | 2 | A | — |  |
| AZ-01 | API, seguridad y documentación | Organizar Azure: importar los lotes, asignar responsables y la iteración 'Sprint 2', vincular HU a Features/Épicas | Requirements | 2 | 1 | A | — |  |
| GIT-01 | Plataforma (CI/CD y calidad) | Acordar y documentar el flujo de ramas (trunk-based, feature/<HU>-<desc>, PR + revisión + CI) y el destino de la rama dev | Requirements | 1 | 1 | C | — |  |
| API-03 | API, seguridad y documentación | Errores y logs según Lineamientos §3.3: traceId (correlación) en ApiError y logs estructurados en JSON — decidir alcance | Development | 3 | 3 | A | — |  |
| PERF-01 | Plataforma (CI/CD y calidad) | Prueba de carga básica del RNF base (200 solicitudes/min, respuesta <= 30 s) con k6 o Gatling sobre login, catálogo y disponibilidad | Testing | 3 | 3 | C | — |  |
| DOC-06 | API, seguridad y documentación | Actualizar la Matriz de HU y la trazabilidad HU→Regla→API→Componente→Tabla→Prueba del Sprint 2 | Documentation | 2 | 2 | B | — |  |
| SP1-07 | Brechas de Sprint 1 | Los rechazos auditados (REJECTED) se perdían al revertirse la transacción: noRollbackFor en login, registro, aprovisionamiento y cambio de rol | Development | 2 | 1 | A | — | ✔ 2026-10-07 · noRollbackFor; AuditPersistenceIntegrationTest |
| SP1-08 | Brechas de Sprint 1 | Las pruebas de integración de Sprint 1 no corrían (Jackson 3, Testcontainers vs Docker 29) y el límite de registro las rompía | Development | 3 | 1 | A | — | ✔ 2026-10-07 · 258 pruebas en verde con Docker |
| OWASP-03 | API, seguridad y documentación | IP real del cliente detrás del proxy de Render (server.forward-headers-strategy=native) para los límites de intentos y la auditoría; verificar en el despliegue | Development | 1 | 2 | A | — |  |
| SEC-04 | Higiene de seguridad | Pedir la contraseña también en POST /auth/mfa/setup: con un token robado de un administrador sin MFA se podría enrolar el autenticador del atacante | Development | 3 | 3 | A | — |  |

