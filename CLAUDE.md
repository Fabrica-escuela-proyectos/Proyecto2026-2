# CLAUDE.md — Proyecto2026-2 (Plataforma de Reservas de Servicios, Fábrica Escuela / CodeF@ctory UdeA)

Instrucciones para cualquier sesión de Claude Code en este repo. Responde y documenta **en español**. Contexto largo y estado actual: `docs/sprint-2/handoff-contexto.md` (léelo después de este archivo). Si este archivo y el repo se contradicen, gana el repo: verifica y corrige.

## Qué es
Backend Spring Boot **4.1.1 / Java 17** (monolito modular), PostgreSQL 16, Flyway, JWT + tabla de sesiones, Docker. Código en `reservas-backend/`, documentación en `docs/`. Repo **público** en GitHub (`Fabrica-escuela-proyectos/Proyecto2026-2`); backlog en Azure DevOps (proceso **Agile**: User Story / Task / Bug). Demo desplegada en Render: `https://proyecto2026-2-5zoo.onrender.com` (`/actuator/health`; se duerme a los 15 min, tarda ≈ 2 min en despertar).
Equipo de Arquitectura de Software: **Simon Betancur** (el usuario), **Juan Esteban González**, **Santiago Rendón**; BD: Andraus + ellos; Calidad: 7 personas (reportan bugs como GitHub Issues, definen Sonar/Quality Gate, asignan los `CP-*`).

## Reglas de trabajo (obligatorias)
1. **Scrum/Azure (política del 2026-10-07: pocas tareas, nivel arquitectónico):** Azure lleva **una tarea por componente o parte desarrollada** de la aplicación (hoy 28 tareas en el lote `L01`). **No** generan tarea: correcciones y bugs (van como `Fixes #n` en el PR), refactors, ajustes, pruebas, documentación suelta, estudio ni higiene. Ante cada cambio: (a) si ya lo cubre una tarea existente (`HU09-API`, `SEG-01`…), no agregues nada y dile al usuario cuál pasa a `Active`/`Closed`; (b) solo si es un componente nuevo no cubierto, agrega UNA `chg(...)` en `docs/sprint-2/azure-boards/backlog_data.py` en un **lote nuevo** (nunca reutilizar uno ya subido), corre `python docs/sprint-2/azure-boards/generar.py` y dile qué CSV de `lotes/` subir (código `[CHG-nnn]` en el commit/PR). El detalle fino vive en `PENDIENTES` (checklist interno, no se sube). Detalle: `docs/sprint-2/azure-boards/README.md`.
2. **Continuidad:** al terminar un bloque de trabajo, actualiza `docs/sprint-2/handoff-contexto.md` (§7 próximos pasos y §9 bitácora). Tu contexto es finito: lo que no esté escrito ahí o en el repo se pierde.
3. **Git:** no hagas `commit`/`push` salvo que el usuario lo pida (él suele commitear desde su terminal). Nunca `push --force` a `main`; para deshacer algo compartido usa `git revert`. Nunca usar "Add files via upload" de GitHub (rompió `main` el 13 sep). Flujo oficial: trunk-based, ramas cortas `feature/<HU>-<desc>` / `fix/<BUG>-<desc>`, PR con revisión de otra persona y CI verde.
4. **Seguridad:** el repo es público: **cero** credenciales, contraseñas, tokens ni cadenas de conexión en archivos versionados (variables de entorno). No registrar contraseñas, tokens ni secretos MFA en logs/auditoría.
5. **Calidad:** todo código nuevo con pruebas unitarias (AAA, Mockito) y, si hay flujo, prueba de integración; cobertura ≥ 65%; Quality Gate de Sonar (deuda ≤ 2 días, complejidad < 50, 0 vulnerabilidades críticas). Definición de hecho = Lineamientos §9.1 (ver plan §10).
6. **Verifica antes de afirmar:** no des por probado lo que solo se compiló; di explícitamente qué corriste y qué no (p. ej. Docker apagado = no se corrieron las pruebas de integración). Antes de acciones destructivas o visibles para otros (push, borrar ramas, parar servicios, rotar claves) pide confirmación.

## Comandos (Windows, Git Bash de la herramienta)
```bash
export PATH="/usr/bin:/bin:/mingw64/bin:$PATH"            # el PATH de la herramienta viene incompleto
export JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"   # JDK 17 OBLIGATORIO (con JDK 24 Lombok no genera código y no avisa)
cd reservas-backend
./mvnw -B clean test jacoco:report   # TODO: 491 pruebas (313 unitarias + 178 de integración); necesita Docker encendido; ≈ 1 min; cobertura en target/site/jacoco/jacoco.csv
./mvnw -B test -Dtest='!*IntegrationTest,!ReservasBackendApplicationTests'   # solo unitarias (313), sin Docker
./mvnw spring-boot:run               # perfil dev; Postgres: docker compose up -d (usuario/BD reservas_app/reservas; clave de dev en application-dev.yml)
```
Primer admin: variables `BOOTSTRAP_ADMIN_EMAIL/PASSWORD/CELLPHONE` (solo actúa si no existe un admin; quitar la contraseña después). **Desde ADR-004 ese admin debe enrolar su MFA** (`/auth/mfa/setup` + `/activate`) para poder usar cualquier otra ruta. Docker Desktop: si está apagado, `Start-Process "C:\Users\Simon\AppData\Local\Programs\DockerDesktop\Docker Desktop.exe"` en PowerShell (≈ 1 min, verificar con `docker ps`). El servicio de Windows `postgresql-x64-17` debe estar detenido (Manual) para liberar el 5432 — detenerlo lo bloquea el clasificador de permisos, pídeselo al usuario.

## Trampas conocidas
- Spring Boot 4 movió autoconfiguraciones: Flyway necesita `spring-boot-flyway` y las migraciones van en `src/main/resources/db/migration/`; `@AutoConfigureMockMvc` está en `spring-boot-webmvc-test`.
- `Set.of(...)` (inmutable) sobre una entidad gestionada rompe `merge()`: usar `new HashSet<>(...)`.
- Hibernate valida el esquema (`ddl-auto: validate`); un cambio de entidad sin migración falla al arrancar.
- **Pruebas de integración:** extienden `AbstractIntegrationTest` (un PostgreSQL de Testcontainers por corrida; ya no usan `localhost:5432`). Docker Engine 29 exige **Testcontainers ≥ 1.21.4** (con 1.21.3: "Could not find a valid Docker environment"; era la causa real del "problema de npipe" de Sprint 1). Usa `uniqueEmail()`/`uniquePhone()` y `createEnrolledAdmin()` (admin con MFA) del helper; la base y el contexto se comparten entre clases. Jackson 3: `tools.jackson.databind.ObjectMapper`, no `com.fasterxml`.
- **Auditoría y transacciones:** un servicio `@Transactional` que audita un rechazo y luego lanza la excepción **pierde el evento** por el rollback; declara esa excepción en `noRollbackFor` (ver `AuthServiceImpl.login`, `UserRegistrationService.register`).
- **Límites de intentos** (`security.rate-limit.*`): registro 5 por IP (el 6.º → 429), login/MFA 5 fallos → 429; el perfil `test` relaja el de registro. En memoria, una sola instancia.
- **`bookings` usa `EXCLUDE USING gist` (migración V10, extensión `btree_gist`):** anti-overbooking en la base por recurso. En Testcontainers y docker-compose funciona (usuario superusuario); **en Render hay que verificar que el usuario de la base pueda crear la extensión** (es "trusted" desde PG 13). `ddl-auto: validate` exige `INT` (no `SMALLINT`) para campos `int`.
- JaCoCo acumula datos entre corridas: para medir cobertura usa `clean`. `.github/` debe estar en la **raíz** del repo para que GitHub lo lea (ya está ahí). El workflow `.github/workflows/build.yml` (tests → Sonar → build) usa `defaults.run.working-directory: reservas-backend` porque el `pom.xml` no está en la raíz; la config de Sonar vive en las propiedades del `pom.xml` y necesita el secret `SONAR_TOKEN`. El job final `Deploy to Render` (solo en `main`) llama al deploy hook (secret `RENDER_DEPLOY_HOOK_URL`; Auto-Deploy de Render en Off) y espera `/actuator/health`; funcionando desde el run #5 (2026-10-08).
- PDF: `Read` no los renderiza; usa `import fitz` (PyMuPDF). `/tmp` de bash ≠ `/tmp` de Python nativo: usa el scratchpad. `export PYTHONIOENCODING=utf-8`. No hay `gh`; la API pública de GitHub sirve con `curl`.
- Documentos de la materia fuera del repo: `C:\Users\Simon\Desktop\semestre_2026-2\FabricaEscuela\` (HUSprint2.md, historias-usuario-sprint-2.docx, Sprint 2 ArquisuaveBD.docx, Lineamientos, Rúbrica, etc.).

## Mapa de la documentación
| Qué | Dónde |
|---|---|
| Plan del Sprint 2, MFA y bugs, estudio, backlog de Azure, estado vivo | `docs/sprint-2/` (`plan-de-trabajo-sprint-2.md`, `cierre-pendientes-sprint-1.md`, `plan-estudio-y-sustentacion.md`, `azure-boards/`, `handoff-contexto.md`) |
| Arquitectura y ADR (001 monolito modular, 002 autenticación, 003 módulos/interfaces) | `docs/arquitectura/` |
| Contratos de API y errores (Sprint 1 y 2: códigos `MFA_REQUIRED`, `X-MFA-Code`, límites) | `docs/api/` (`errores-api-sprint-2.md`) |
| Política de MFA (ADR-004) y resultados de pruebas del Sprint 2 | `docs/arquitectura/adr/ADR-004-politica-mfa.md`, `docs/resultados-pruebas-sprint-2.md` |
| Modelo de BD formal vs esquema real, y por qué UUID/CASCADE | `docs/bd/modelo/`, `docs/conciliacion-modelo-bd-sprint-1.md` |
| Criterios de aceptación Sprint 1 verificados (39 criterios) | `docs/verificacion-criterios-aceptacion-sprint-1.md` |
| Estado y resultados de pruebas | `docs/estado-proyecto-sprint-1.md`, `docs/resultados-pruebas-sprint-1.md` |
| Correr local / desplegar / probar lo desplegado | `docs/guia-desarrollo-local.md`, `docs/guia-despliegue-render.md`, `docs/guia-prueba-aplicacion-desplegada.md` |
| Plantilla de bugs de Calidad | `.github/ISSUE_TEMPLATE/bug_report.yml` |

## Estado en una línea
Sprint 1 (HU-01..06) completo y desplegado; Sprint 2 (HU-07..28, Azure IDs 72–93): corte Arquisoft/BD **13 oct**, review **20 oct**. Al 2026-10-07 están hechos (sin commitear ni desplegar) los bugs de Calidad #8–#11 y el MFA mínimo (ADR-004), con 491 pruebas en verde. **HU-09 (crear servicio), HU-08 (antelación mínima), HU-13 (catálogo), HU-14 (recursos) y HU-18 (asignar recursos a servicios) , HU-19 (horarios de recursos) y HU-20 (disponibilidad, pública) y HU-22 (crear reserva) implementadas** (`service/`, migración V5, `docs/api/endpoints-sprint-2.md`); HU-08 pasó a Tier 1. Pendientes críticos: aprobar ADR-004, CI/Sonar, credenciales demo expuestas, convenciones de BD (UUID, sin `RESTRICT` hacia `users`), y empezar las HU de servicios/recursos/reservas. Ver `handoff-contexto.md` §7.
