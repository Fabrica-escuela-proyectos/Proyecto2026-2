# Handoff de contexto — para retomar el trabajo sin el historial de la conversación

> **Documento vivo.** Lo lee primero cualquier sesión nueva (después de `CLAUDE.md`). Al terminar cada bloque de trabajo hay que **actualizar §7 (próximos pasos) y §9 (bitácora)**. Si algo de aquí contradice lo que ves en el repo, **gana el repo**: verifícalo (`git log`, leer el archivo) y corrige este documento. Última actualización: **2026-10-07 (noche)**.

## 1. Cómo retomar (checklist de 5 minutos)

1. Leer `CLAUDE.md` (reglas, comandos, trampas) y este archivo completo.
2. `git status -sb` y `git log --oneline -10`: ver qué cambió desde la última entrada de la bitácora (el usuario, Simon, suele commitear él mismo desde su terminal; los compañeros también suben cosas). **Hay trabajo sin commitear** (ver §7).
3. Abrir `docs/sprint-2/plan-de-trabajo-sprint-2.md` (qué se hace y por qué) y `docs/sprint-2/azure-boards/tareas-sprint-2.md` (tareas de Azure, checklist interno y avance).
4. `python docs/sprint-2/azure-boards/generar.py` debe correr sin errores (valida los datos del backlog).
5. Con Docker encendido, `cd reservas-backend && ./mvnw -B clean test` debe dar 258 pruebas en verde (ver §6 para encender Docker y el JDK).
6. Preguntar al usuario qué tarea/código quiere atacar; **no asumir** que el estado de este archivo sigue vigente si pasaron días.

## 2. Personas

| Quién | Rol | Notas |
|---|---|---|
| **Simon Betancur Sosa** (el usuario) | Arquitectura de Software; backend, seguridad, puente con BD | Habla español; commitea/pushea él mismo; correo `simonbetansosa@gmail.com` (git) |
| **Juan Esteban González Duque** | Arquitectura (diseño, contratos, ADR) y BD/consultas | Handle de GitHub `Juanesgodu` (inferido) |
| **Santiago Rendón Rivera** | Arquitectura (calidad técnica: repo, ramas, pruebas, despliegue) y BD/seguridad | `santiago.rendonr@udea.edu.co`; hizo el incidente del 13 sep (ver §5) |
| **Juan Sebastián Andraus López** | Bases de Datos (principal) | `jsandraus` |
| **Calidad** (7 personas) | Casos de prueba, Sonar/Quality Gate, bugs, pruebas unitarias AAA | Handles vistos: `jddeoro20`, `Miguel-Gallego-2` (reportan bugs), `wanerge` (plantilla de bug), `leniaso` (Alejandro Naranjo; PR #6 con pruebas y JaCoCo); en Azure: Mariana Martínez Gaviria (HU 22–24) y Miguel Ángel Gallego Bedoya (HU 25–28) |
| **Docencia** | — | Docente que aparece en el PPT de GitFlow: Juan Felipe Quintana Gómez; Lineamientos preparados por Catalina Céspedes; curso Fábrica Escuela / CodeF@ctory UdeA, "Caso 14" |

Los "3 de Arquisoft" = Simon, Juan Esteban y Santiago. Los de BD = ellos + Andraus. Scrum Master / Product Owner: **no aparecen** en los documentos.

## 3. El proyecto en una página

- **Qué:** Plataforma de Reservas de Servicios (backend). Monolito modular Spring Boot **4.1.1**, **Java 17**, PostgreSQL 16, Flyway, JWT + tabla de sesiones, Docker. Repo público `Fabrica-escuela-proyectos/Proyecto2026-2`; el código vive en `reservas-backend/`, la documentación en `docs/`.
- **Producción/demo:** `https://proyecto2026-2-5zoo.onrender.com` (Render: Web Service gratis + Postgres 16 gratis; se duerme a los 15 min y tarda ≈ 2 min en despertar; la BD gratuita **expira a los 90 días ≈ 2026-12-21**). Health: `/actuator/health`. Se despliega automáticamente desde `main`. Variables en Render: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `SPRING_PROFILES_ACTIVE=prod` (+ `BOOTSTRAP_ADMIN_*` solo para crear el primer admin; opcionales `RATE_LIMIT_REGISTRATION_MAX_ATTEMPTS` y `RATE_LIMIT_AUTH_MAX_ATTEMPTS`). El admin demo existe (ver `docs/guia-prueba-aplicacion-desplegada.md`; **su contraseña está expuesta en el repo público — SEC-01**). **Render sigue con el código del Sprint 1**: lo hecho el 2026-10-07 no está desplegado ni commiteado.
- **Sprint 1 (HU-01..06) completo y desplegado:** registro de cliente y proveedor, login con JWT de 1 h, logout (revoca todas las sesiones), roles/permisos de administración, control de acceso por rol y pertenencia, MFA (TOTP), auditoría.
- **Sprint 2 (en curso):** Épicas 1 (proveedores y servicios), 2 (recursos), 3 (reservas); HU-07..28 en Azure (IDs 72–93).
- **Inventario técnico al 2026-10-07 (noche):** migraciones `V1`–`V4` (`users`, `roles`, `user_roles`, `audit_logs`, `sessions`, `providers`, `businesses`, `mfa`); **258 pruebas en verde** (195 unitarias + 63 de integración con PostgreSQL real en Testcontainers); cobertura de líneas **95,2 %** con integración y **84,6 %** solo unitarias (línea base 77,1 %). **Sin CI activo, sin Sonar, sin Swagger todavía.**
- **Endpoints existentes:** `POST /api/v1/users` · `POST /api/v1/providers` · `POST /api/v1/auth/login` (admin con MFA: `mfaCode`) · `POST /api/v1/auth/logout` · `POST /api/v1/auth/mfa/setup|activate` · `GET /api/v1/users/{id}` · `PATCH /api/v1/users/{id}/role` y `DELETE /api/v1/users/{id}` (ambos con header `X-MFA-Code`) · `GET /api/v1/providers/me|{id}` · `/actuator/health`. Códigos de error y cambios de contrato: `docs/api/errores-api-sprint-2.md`.
- **Seguridad de acceso ya implementada (ADR-004):** MFA obligatoria para ADMINISTRADOR (`MfaEnrollmentFilter` → `403 MFA_ENROLLMENT_REQUIRED`), login en dos pasos (`401 MFA_REQUIRED`), confirmación `X-MFA-Code` en rol y borrado (`StepUpService`), control de intentos (`AttemptLimiter`: 5 fallos → 429; registro: el 6.º intento por IP → 429). Pendiente de la política: anti-replay, cifrado del secreto, reinicio por otro admin.

## 4. Fechas y evaluación

| Hito | Fecha |
|---|---|
| Sprint 2 | 29 sep → review **20 oct** |
| Corte Arquisoft/BD (regla: una semana antes de la review) | **13 oct** (meta del equipo 12; tope 15) |
| Pruebas de Calidad | 15 → 20 oct |
| Planning Sprint 3 | 27 oct |

Rúbrica: funcional backend **30%** (100% de lo *comprometido*) · arquitectura **25%** · calidad interna **25%** (Sprint 2: cobertura ≥ 65%, deuda ≤ 2 días, complejidad < 50, severidad Minor+, 0 vulnerabilidades críticas) · presentación **20%**. Requisitos de Arquisoft S2: diagrama de despliegue, APIs priorizadas, ≥ 3 APIs REST, CI/CD con GitHub Actions, OWASP Top 10, documentación (Swagger). Lineamientos clave: trunk-based con rama protegida; DoD §9.1; ADR con contexto/alternativas/elección/consecuencias/responsable/fecha; MFA para accesos administrativos o sensibles (§3.4, §6.2); errores con `errorCode/details/traceId` (§3.3, hoy no cumplido).

## 5. Decisiones y su razón (para no re-litigarlas)

| Decisión | Por qué |
|---|---|
| Monolito modular, módulos por dominio, comunicación por interfaces (ADR-001/003) | Equipo pequeño, poco tiempo, dominios relacionados |
| JWT de 1 h + tabla `sessions` con `jti` (ADR-002) | Revocación en logout; sin refresh |
| UUID como PK en todo el backend | No enumerable; ya expuesto en DTO y JWT. El modelo formal de BD usa BIGSERIAL → **no migrar la app**, alinear los documentos (`docs/conciliacion-modelo-bd-sprint-1.md`) |
| `audit_logs` sin FK a `users`; `sessions`/`providers`/`mfa` con `ON DELETE CASCADE` | HU-05: eliminar usuario conservando historial. `RESTRICT` lo rompería |
| **JDK 17 obligatorio** | Con JDK 24 Lombok **no genera código y el compilador no avisa** |
| `spring-boot-flyway` como dependencia explícita y migraciones en `src/main/resources/db/migration/` | En Spring Boot 4 la autoconfiguración de Flyway se movió a su propio artefacto; sin eso Flyway nunca corre |
| `spring-boot-webmvc-test` para `@AutoConfigureMockMvc` | Mismo reordenamiento de paquetes de Boot 4 |
| Perfil `dev` con contraseña de BD por defecto; producción con `SPRING_PROFILES_ACTIVE=prod` y variables | Comodidad local sin secretos reales |
| Primer admin por `AdminBootstrapRunner` (variables `BOOTSTRAP_ADMIN_*`, solo si no hay admin) | Sin endpoint público ni secretos en el repo |
| Formato de error `{timestamp,status,error,message,path,fields}` | Decidido en Sprint 1 (`errores-api-sprint-1.md`); difiere de los Lineamientos. Los códigos nuevos van en el campo `error` (sin cambiar el esquema) |
| Revertir (no force-push) los commits de Santiago del 13 sep | Preserva el historial compartido; `git revert 5ae30bd` |
| **Azure: pocas tareas, una por componente; correcciones, bugs, pruebas y docs no generan tarea** (2026-10-07) | Lo pidió Simon: la primera versión (173 tareas) era demasiado detallada. El detalle fino vive en `PENDIENTES` (no se sube) |
| **Política de MFA (ADR-004, estado Propuesto):** TOTP, obligatoria para ADMINISTRADOR con enrolamiento forzoso (no se bloquea la cuenta), login en dos pasos, `X-MFA-Code` en operaciones sensibles, 5 fallos → 429 | Cierra "obligatorio = solo un evento"; ver `cierre-pendientes-sprint-1.md` §2 |
| Límites de intentos configurables (`security.rate-limit.*`), relajados solo en el perfil `test` | Las pruebas de integración registran decenas de cuentas desde una IP; el límite real se prueba en `RegistrationRateLimitIntegrationTest` |
| `noRollbackFor` en los servicios que auditan un rechazo y luego lanzan la excepción | Sin eso el rollback borraba el evento `REJECTED` (SP1-07). **Regla para código nuevo:** si un servicio `@Transactional` audita y lanza, declarar la excepción en `noRollbackFor` |
| Testcontainers **1.21.4** y base común `AbstractIntegrationTest` (contenedor único) | 1.21.3 no habla con Docker Engine 29; ReservasBackendApplicationTests ya no depende de `localhost:5432` |
| **Propuestas del Sprint 2 (pendientes de aprobación del equipo):** niveles de alcance Tier 1/2/3; anti-overbooking con `EXCLUDE` por recurso; snapshots y `SET NULL` en `bookings`; evento síncrono para HU-28; patrón `409 CONFIRMATION_REQUIRED`; migraciones numeradas por una sola persona | Ver `plan-de-trabajo-sprint-2.md` §5 |

## 6. Trampas del entorno (Windows + herramientas de la sesión)

- **Bash de la herramienta:** el PATH suele venir sin `/usr/bin`; empezar los comandos con `export PATH="/usr/bin:/bin:/mingw64/bin:$PATH"`. Para Docker añadir `/c/Users/Simon/AppData/Local/Programs/DockerDesktop/resources/bin`. `cd` persiste entre llamadas en esta herramienta (ojo con el directorio de trabajo). **Heredocs largos con comillas mezcladas fallan** ("unexpected EOF"): escribe los scripts con la herramienta Write y ejecútalos.
- **JAVA_HOME:** `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot` (en bash: `export JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"`). El JDK por defecto de la máquina es 24: **no** compilar con él.
- **Python** (3.12) existe. Salida con tildes: `export PYTHONIOENCODING=utf-8 PYTHONUTF8=1`. `/tmp` en bash y en Python nativo **no son la misma carpeta**: guardar archivos temporales en el *scratchpad* con ruta Windows.
- **PDF:** la herramienta `Read` no los renderiza (falta `pdftoppm`); usar `import fitz` (PyMuPDF) o `C:\Program Files\Git\mingw64\bin\pdftotext.exe`. **.docx:** leer `word/document.xml` del zip.
- **No hay `gh`**; la API pública de GitHub funciona con `curl` (issues: `https://api.github.com/repos/Fabrica-escuela-proyectos/Proyecto2026-2/issues`).
- **Docker Desktop:** si está apagado, se enciende con PowerShell `Start-Process "C:\Users\Simon\AppData\Local\Programs\DockerDesktop\Docker Desktop.exe"` (tarda ≈ 1 min; comprobar con `docker ps`). Las pruebas de integración lo necesitan. Docker Engine 29 exige **Testcontainers ≥ 1.21.4** (con 1.21.3: "Could not find a valid Docker environment").
- **Servicio nativo `postgresql-x64-17`:** debe estar detenido (inicio Manual) para que el Postgres de Docker use el 5432. Detener servicios de Windows lo bloquea el clasificador de permisos: pedírselo al usuario.
- **Maven:** `./mvnw -B test` (todo). Solo unitarias: `-Dtest='!*IntegrationTest,!ReservasBackendApplicationTests'`. Cobertura: `./mvnw -B clean test jacoco:report` → `target/site/jacoco/jacoco.csv` (sin `clean`, JaCoCo acumula datos de corridas anteriores y los números engañan).
- **Edit/Write:** si un archivo cambió en disco desde que lo leíste (sed, otro editor), la herramienta pide releerlo antes de escribir.
- **Git:** `docs/` y el código llegan por commits de varios; antes de editar un archivo compartido, mirar `git log -- <archivo>`. Nunca "Add files via upload" (provocó el incidente del 13 sep: se perdió `spring-boot-flyway`, se duplicaron migraciones y se filtró una clave de Supabase). Los finales de línea avisan `LF will be replaced by CRLF`: es normal en esta máquina.
- **Pruebas de integración nuevas:** extender `AbstractIntegrationTest`, usar `uniqueEmail()`/`uniquePhone()` (la base se comparte entre clases) y, para administradores, `createEnrolledAdmin()` (completa el enrolamiento de MFA y devuelve token, secreto y `code()`); los códigos TOTP los genera `TotpTestSupport`, independiente del código de producción.

## 7. Estado de lo pedido el 2026-10-07 y próximos pasos

**Hecho y verificado (sin commitear; Simon commitea desde su terminal):**
- Planificación y continuidad: plan de trabajo, cierre de pendientes, plan de estudio/sustentación, backlog de Azure simplificado (28 tareas, lote `L01`), trazabilidad, este handoff y `CLAUDE.md`.
- **Bugs de Calidad #8–#11 corregidos** (límite de registro atómico, longitud de campos, 409 en concurrencia, manejadores 4xx) con regresión medida antes/después.
- **MFA mínimo (ADR-004 P1–P7, P11):** enrolamiento obligatorio, login en dos pasos, confirmación en operaciones sensibles, control de intentos. Verificado también contra la app real (28/28).
- Pruebas de integración de Sprint 1 reparadas (Jackson 3, Testcontainers 1.21.4, límite configurable) y auditoría de rechazos persistente.
- Documentación: ADR-004, `docs/api/errores-api-sprint-2.md`, `docs/resultados-pruebas-sprint-2.md`, guía de prueba desplegada y guía local actualizadas.

**Cómo está el árbol de trabajo:** cambios sin commit en `reservas-backend/` (código y pruebas), `docs/` y `CLAUDE.md`. Rama sugerida (trunk-based): `fix/BUG-8-11-limites-y-errores` + `feature/MFA-politica-adr004`, o una sola `feature/sprint2-mfa-y-bugs`. Commit/PR: citar `Fixes #8 #9 #10 #11`. Ojo: el usuario movió `.github/` dentro de `reservas-backend/` (ver §8).

**Tareas de Azure (lote L01, aún sin importar):** `SEG-01` → Active (6 de 7 ítems; falta la prueba con app autenticadora real, colección Postman y evidencia); `SEG-02` → Closed tras la revisión del PR. Las demás siguen en New.

**Siguiente, en orden:**
1. El usuario revisa y commitea; decide el alcance (Tier 1/2) y los supuestos de `plan §7` con el equipo y Calidad; **la aprobación de ADR-004 por Juan Esteban y Santiago**.
2. Importar `lotes/azure-import_L01_tareas-por-componente.csv` a Azure y anotar los IDs de las historias técnicas en `TECH_IDS`.
3. Día 1–2 urgente: **BD-01** (convenciones UUID/ON DELETE antes de que BD diseñe), **SEC-01/02** (rotar credenciales; con el MFA obligatorio, enrolar al admin del bootstrap justo después de desplegar), **CI-02** (permisos de Sonar), **PLT-01** (mover el workflow a `.github/workflows/` de la raíz y adaptarlo: `working-directory: reservas-backend`, ramas/PR, Docker ya viene en `ubuntu-latest`).
4. Cuando el usuario lo pida: pendientes de MFA (anti-replay MFA-06, cifrado MFA-07, reinicio MFA-08), `SP1-01` (cambiar contraseña con confirmación), `OWASP-03` (IP real detrás del proxy de Render), y después **HU-09 en adelante** según el plan.
5. Antes de desplegar a Render: que el admin demo enrole su MFA, actualizar la guía del profesor, y valorar subir `RATE_LIMIT_REGISTRATION_MAX_ATTEMPTS` solo en el entorno de pruebas de Calidad.

## 8. Preguntas abiertas y bloqueos externos

- Alcance definitivo del sprint (15 vs 22 HU vs niveles) y los 15 supuestos de `plan §7`.
- **Ubicación de `.github/`:** durante la sesión la carpeta `.github/` de la raíz (con `ISSUE_TEMPLATE/bug_report.yml`, la plantilla de Calidad) apareció como borrada y movida a `reservas-backend/.github/`, junto con un `workflows/build.yml` nuevo (CI + SonarCloud con proyecto y organización personales `Simonbetan_Lab2p2026` / `simonbetan`). GitHub solo lee `.github/` en la **raíz** del repo; tal como está, el workflow no corre y la plantilla de bugs dejaría de aparecer. No lo toqué: es trabajo en curso de Simon.
- Permisos de Sonar: ¿quién es admin/owner de la organización GitHub `Fabrica-escuela-proyectos`? Calidad pidió acceso u ownership.
- Fecha y formato de la sustentación (no aparecen en los documentos).
- ¿Se rotó la contraseña de Supabase expuesta (commit `5ae30bd`)? ¿Se rotó la del admin demo?
- ¿Se exige el formato de error de los Lineamientos (`errorCode/details/traceId`)?
- Nombres exactos de Azure ("NOMBRE <correo>") para rellenar `EQUIPO`; nombre de la iteración "Sprint 2".
- ¿Se importó alguna vez alguno de los 7 CSV de la primera versión (173 tareas)? Si sí, hay que borrarlos de Azure antes de importar `L01` (ver `azure-boards/README.md` §2).

## 9. Bitácora (añadir al final; una entrada por sesión de trabajo)

| Fecha | Qué se hizo | Archivos | Avance en `AVANCE` / Azure |
|---|---|---|---|
| 2026-10-07 (tarde) | Lectura de documentos del Sprint 2 (HUSprint2.md, historias-usuario-sprint-2.docx, Sprint 2 ArquisuaveBD.docx), rúbrica, lineamientos y issues de Calidad (#8–#11); línea base de cobertura (101 pruebas, 77,1%); plan de trabajo, cierre de pendientes, plan de estudio, generador del backlog de Azure, `CLAUDE.md`, este handoff, memoria | `docs/sprint-2/**`, `CLAUDE.md` | — |
| 2026-10-07 (noche) | Simplificación de Azure (173 → 28 tareas por componente, lote único `L01`; los bugs y el detalle fino quedan en `PENDIENTES`). Implementación de los bugs #8–#11 y del MFA mínimo; reparación de las pruebas de integración (Jackson 3, Testcontainers 1.21.4, `AbstractIntegrationTest`); auditoría de rechazos persistente; ADR-004, errores del Sprint 2, resultados de pruebas, guías actualizadas; verificación E2E contra la app real | `reservas-backend/**` (código y pruebas), `docs/arquitectura/adr/ADR-004-politica-mfa.md`, `docs/api/errores-api-sprint-2.md`, `docs/resultados-pruebas-sprint-2.md`, `docs/sprint-2/**`, `docs/guia-*.md`, `CLAUDE.md` | BUG-8..11, MFA-01..05, 09, 10, SP1-05, 07, 08 hechos; MFA-11 y 12 parciales. Azure: `SEG-01` Active, `SEG-02` listo para Closed tras revisión |

- **2026-10-08 — CI/CD.** `.github/workflows/build.yml` corregido (el `pom.xml` está en `reservas-backend/`: `defaults.run.working-directory`), con jobs tests → SonarCloud → build → `Deploy to Render` (deploy hook, secret `RENDER_DEPLOY_HOOK_URL`, Auto-Deploy de Render en Off; smoke test a `/actuator/health`). Run #5 en verde (Simon lo verificó en GitHub). Cierra SP1/PLT-01 en lo esencial; el despliegue del JAR del pipeline como imagen (GHCR) se descartó: no es obligatorio. Pendiente: confirmar permisos de Sonar y que el Quality Gate se evalúe.

- **2026-10-08 — Sonar.** El proyecto `Fabrica-escuela-proyectos_Proyecto2026-2` ya es público (API sin token: `curl "https://sonarcloud.io/api/issues/search?componentKeys=<key>&resolved=false"`). Estado: gate OK (solo código nuevo), cobertura 93,7 %, deuda 75 min, 8 issues. Corregidos en código (sin commitear): S2119 (`SecureRandom` estático), S6829 (`@Autowired` en `TotpService`), S8786 ×3 (`PasswordValidator` con `Pattern.find`), S2699 (`contextLoads` con aserción); 258 pruebas en verde. **Pendiente en la UI de Sonar (requiere admin):** marcar S4790 (SHA-1 es el estándar de TOTP, RFC 6238) y S4502 (CSRF desactivado: API sin estado con JWT en cabecera, sin cookies) como Accepted/Safe con justificación. Las 2 vulnerabilidades críticas bajan a 0 solo así. Complejidad: 275 total / 116 cognitiva; falta saber cómo la mide Calidad.

- **2026-10-08 — Import de Azure (L01).** Primer intento: se guardaron las 15 historias existentes (sin cambios reales) y fallaron 31 filas por `TF401289` (sin permiso para crear **tags**). CSV regenerado **sin columna Tags** y con `State=New` en las historias existentes; SEG-01 = Active, SEG-02 = Closed. Verificado en Azure (solo lectura): 22 items (72–93), todos `New`, títulos idénticos; HU 13–18 (IDs 78–83) sin story points en Azure. Pendiente: reimportar, anotar los IDs de TECH-01..03 en `TECH_IDS` y marcar L01 como subido.

- **2026-10-08 — L01 subido.** Verificado en Azure por API: 28 tareas (348 h) y 3 historias técnicas, 0 tareas sin padre. IDs: TECH-01=114 (SEG-01=115, SEG-02=116), TECH-02=117, TECH-03=120. Registrados en `TECH_IDS`; `ESTADO_LOTES` L01=subido (los cambios nuevos van en un lote L02+). Azure creó SEG-01/SEG-02 en `New` (ignoró el estado del CSV): pasar a mano SEG-01→Active y SEG-02→Closed.

- **2026-10-08 — Taskboard vacío.** Las 28 tareas y 3 historias TECH (IDs 114–145) quedaron con `Iteration Path = "Service Booking Plataform"` (raíz) porque el CSV no la traía → no salen en el Taskboard del Sprint 2. `generar.py` ya la incluye (`ITERACION`) para lotes nuevos. Las ya subidas hay que moverlas a `Service Booking Plataform\Sprint 2` (edición masiva en Backlogs o por API).

- **2026-10-08 — Iteración corregida por API.** Los 31 ítems (IDs 114–145) movidos a `Service Booking Plataform\Sprint 2` (31/31 con HTTP 200, verificado: 25 historias y 28 tareas en Sprint 2; SEG-01 Active y SEG-02 Closed ya están así en Azure). Ya salen en el Taskboard.

- **2026-10-08 — HU-08 a Tier 1, BD-01 y HU-09 (sin commitear).** Decisión del usuario: HU-08 entra al compromiso firme (plan §2; su tarea ya existía en L01, no hace falta lote nuevo). Escrito `docs/bd/convenciones-bd.md` (propuesta BD-01, falta acordar con BD). **HU-09 implementada:** migración `V5__create_services_table.sql`; módulo `service/` (`ServiceOffering`, repositorio, `ServiceOfferingService(Impl)`, `ServiceController`: `POST/GET /api/v1/businesses/{businessId}/services`, solo PROVEEDOR dueño); `provider/application/BusinessAccessService(Impl)` es el contrato de pertenencia que usarán HU-10..24 (`requireOwner`, 404/403); `BusinessNotFoundException`, `DuplicateServiceNameException`, `AuditEventType.CREACION_SERVICIO`; contrato en `docs/api/endpoints-sprint-2.md`. 28 pruebas nuevas (unitarias + `ServiceCreationIntegrationTest`, incluida la concurrencia 201/409); suite completa **286 en verde**, cobertura 95,4 % líneas / 89,3 % ramas. Supuestos a confirmar con QA/PO: duración 1..1440 min, precio 0..1.000.000.000 COP; ADMIN y proveedor ajeno reciben 403. En Azure: `HU09-DATOS` y `HU09-API` pueden pasar a Active/Closed tras el PR. **Siguiente en el plan:** HU-13 (catálogo, depende de HU-09), HU-14 (recursos), HU-08 (antelación; añade `min_advance_hours` a `businesses`).

- **2026-10-08 — HU-08 implementada (sin commitear).** Migración `V6__add_booking_lead_time_to_businesses.sql` (`min_advance_hours INT NOT NULL DEFAULT 1`, CHECK 1..720); `Business.minAdvanceHours`; en `provider/`: `BusinessSettingsService(Impl)` y `BusinessSettingsController` (`GET`/`PUT /api/v1/businesses/{businessId}/booking-lead-time`, solo PROVEEDOR dueño); `AuditEventType.CONFIGURACION_NEGOCIO`. **`BusinessSettingsService.minAdvanceHoursOf(businessId)` es lo que debe usar HU-22.** Desviación: el GET no está en `GET /businesses/{id}` (reservado al detalle del catálogo de HU-13). 16 pruebas nuevas; suite completa **302 en verde**, cobertura 95,5 % líneas / 89,3 % ramas. Pendiente: CP-HU08-07 y 08 (cuando exista HU-22); confirmar el tope de 720 h con QA/PO. Azure: la tarea `HU08` (historia 73) puede pasar a Active/Closed tras el PR. **Siguiente:** HU-13 (catálogo), luego HU-14 (recursos).
