# Plan de estudio y sustentación — Arquitectura de Software (Sprint 2)

Para **Simon Betancur**, **Juan Esteban González** y **Santiago Rendón**. Objetivo: que los tres entiendan y puedan defender *todo* lo construido y lo que se construye en el Sprint 2 — endpoints, reglas de negocio, flujo de la aplicación, la arquitectura elegida y cómo se materializa en el código — para la review/sustentación del **20 de octubre** (fecha y formato exactos por confirmar con el docente).

Por qué importa: **Presentación del equipo = 20%** y **Arquitectura de solución = 25%** (justificar decisiones con criterios técnicos) de la rúbrica; además, en una sustentación cualquiera puede ser preguntado por cualquier parte. No basta con que una persona "sepa del módulo".

## 1. Modelo de trabajo

Tres **pistas**. Cada pista tiene un **dueño** (la estudia a fondo mientras la construye y la explica a los demás) y un **lector crítico** (la estudia como evaluador: la cuestiona, revisa sus PR y hace las preguntas difíciles). La rotación hace que nadie revise su propia pista.

| Pista | Contenido | Dueño | Lector crítico |
|---|---|---|---|
| **1 · Identidad y seguridad** | HU-01..06, JWT + sesiones, MFA (ADR-004), límite de intentos, OWASP, bugs de Calidad | Simon | Santiago |
| **2 · Dominio de reservas** | Servicios, recursos, horarios, disponibilidad, reservas y cancelaciones (HU-08..28), anti-overbooking, historial, contratos de API | Juan Esteban | Simon |
| **3 · Plataforma y calidad** | Arquitectura (ADR-001/003), BD y Flyway, CI/CD, Docker/Render, Sonar/JaCoCo, Swagger, diagrama de despliegue, flujo Git/Azure | Santiago | Juan Esteban |

Reglas del modelo:
1. **Revisión cruzada de PR (EST-X1):** el PR de una pista lo aprueba su lector crítico (o el otro integrante), nunca su autor. Revisar un PR *es* estudiar: el revisor debe poder explicar el cambio.
2. **Teach-back:** el Día 6 cada dueño explica su pista en 30 min (sin diapositivas, recorriendo el código y Swagger) y recibe preguntas de los otros dos.
3. **Ensayos:** dos ensayos con el banco de preguntas (§5): el primero el Día 7, el segundo el Día 8 con la demo en vivo.
4. **Quien responde:** primero el dueño de la pista; el lector crítico complementa o corrige. Nadie responde "eso lo hizo otra persona".
5. **Entregable de estudio por persona** (para el banco y para repasar): una hoja con el resumen de su pista, un diagrama dibujado a mano de su flujo principal y 10 preguntas propias con respuesta.

## 2. Lecturas obligatorias (todos, Días 1–2, ≈ 2 h en total)

| Documento | Qué sacar de él |
|---|---|
| `docs/arquitectura/adr/ADR-001`, `ADR-002`, `ADR-003` | Decisión, alternativas descartadas, consecuencias positivas y negativas — es el lenguaje de la sustentación |
| `docs/arquitectura/arquitectura-sprint-1.md` (módulos, capas, §6.11 alcance) | Estructura `controller / application / domain / infrastructure` |
| `docs/api/endpoints-sprint-1.md`, `dtos-sprint-1.md`, `errores-api-sprint-1.md` | Contratos y formato de error |
| `docs/estado-proyecto-sprint-1.md`, `docs/verificacion-criterios-aceptacion-sprint-1.md` | Qué hay y qué criterio cumple cada cosa |
| `docs/sprint-2/plan-de-trabajo-sprint-2.md` y `cierre-pendientes-sprint-1.md` | El plan, las decisiones del Sprint 2 y MFA |
| `docs/conciliacion-modelo-bd-sprint-1.md` | Por qué UUID, por qué `CASCADE`/sin FK en auditoría |
| Lineamientos §3 (backend, calidad, seguridad), §7 (Git/CI), §9 (DoD) y la Rúbrica | Contra qué se evalúa |

## 3. Pistas en detalle

### Pista 1 · Identidad y seguridad (dueño: Simon · lector crítico: Santiago)
- **Código:** `identity/controller` (`UserController`, `AuthController`, `MfaController`), `identity/application` (`UserRegistrationService`, `AuthServiceImpl`, `UserManagementServiceImpl`, `MfaServiceImpl`, `AdminBootstrapRunner`), `identity/infrastructure` (`SecurityConfig`, `RegistrationRateLimiter`, `security/JwtTokenProvider`, `JwtAuthenticationFilter`, `TotpService`), `common/error/GlobalExceptionHandler`, `common/security`.
- **Recorridos guiados:**
  1. *Registro (HU-01):* `UserController.register` → `UserRegistrationService.register` (límite → duplicados → rol del servidor → BCrypt → guardar → auditoría) → respuestas en `GlobalExceptionHandler`.
  2. *Login y token (HU-02):* `AuthServiceImpl.login` → `JwtTokenProvider.issueToken` (`jti`, 1 h) → fila en `sessions`; luego `JwtAuthenticationFilter` en **cada** petición (firma, expiración, sesión vigente, usuario habilitado, authorities `ROLE_*`).
  3. *Logout (HU-04):* `revokeAllByUserId` y por qué el mismo token da 401 después.
  4. *Cambio de rol y eliminación (HU-05):* reglas en `UserManagementServiceImpl` (auto-modificación, Proveedor inmutable, rol inexistente, MFA al ascender, historial de auditoría).
  5. *MFA:* política P1–P11 y la matriz TC-MFA.
- **Ejercicio:** con Swagger/Postman, ejecutar el ciclo registro → login → consulta propia/ajena → logout → mismo token (401); activar MFA con una app autenticadora y hacer un `PATCH` de rol con `X-MFA-Code`.
- **Debe poder explicar:** por qué JWT + tabla de sesiones, qué viaja en el token y qué no, por qué el mensaje de login es genérico, cómo se evita la fuerza bruta, qué cubre OWASP A01/A02/A07 en el proyecto.

### Pista 2 · Dominio de reservas (dueño: Juan Esteban · lector crítico: Simon)
- **Código nuevo:** módulos `service`, `resource`, `reservation` (más `provider`); migraciones `V5+`.
- **Recorridos guiados (se completan al construirlos):**
  1. *Preparar un negocio:* crear servicio (HU-09) → crear recurso (HU-14) → asignar (HU-18) → horario (HU-19).
  2. *Disponibilidad (HU-20):* cómo se calculan los horarios libres (horario del recurso − reservas confirmadas − antelación mínima), zona `America/Bogota`, cuadrícula de 30 min.
  3. *Reservar (HU-22):* validaciones → `INSERT` → restricción `EXCLUDE` → 201 o 409; precio en la reserva.
  4. *Cancelar (HU-25/26/16/28):* reglas de 1 h, motivos, patrón `CONFIRMATION_REQUIRED`, evento de eliminación de usuario.
- **Ejercicio:** reservar dos veces el mismo horario en paralelo (dos terminales o una prueba con `CyclicBarrier`) y explicar por qué solo una gana; leer las filas de `bookings` antes y después de eliminar un usuario.
- **Debe poder explicar:** las reglas de negocio acordadas (antelación 1 h, cancelación del cliente 1 h, estados y motivos, precio informativo en COP, confirmación automática), cómo se conserva el historial y por qué el diseño aguanta la concurrencia.

### Pista 3 · Plataforma y calidad (dueño: Santiago · lector crítico: Juan Esteban)
- **Código y configuración:** `pom.xml`, `Dockerfile`, `.github/workflows/ci.yml`, `application*.yml`, `db/migration/V*.sql`, `docker-compose.yml`, `docs/arquitectura/`.
- **Recorridos guiados:**
  1. *De `git push` a producción:* PR → GitHub Actions (build, pruebas, JaCoCo, Sonar, imagen) → merge a `main` → Render construye el `Dockerfile` → arranca con `SPRING_PROFILES_ACTIVE=prod` → Flyway migra → `/actuator/health`.
  2. *Flyway:* por qué `ddl-auto: validate`, qué pasó en Sprint 1 con `spring-boot-flyway` y la carpeta `db/migration`.
  3. *Calidad:* qué mide cada Quality Gate, dónde se ve la cobertura, cómo se interpreta un hallazgo.
  4. *Arquitectura:* módulos, capas, interfaces entre módulos, diagramas de componentes/paquetes/despliegue.
- **Ejercicio:** romper a propósito una prueba en una rama y ver que el pipeline bloquea el merge; levantar el proyecto desde cero con la guía `docs/guia-desarrollo-local.md`.
- **Debe poder explicar:** por qué monolito modular (ADR-001), por qué JDK 17 y no 24 (Lombok no genera código), cómo se separan dev/test/prod, qué limita el plan gratuito de Render (cold start de ≈ 2 min; la BD gratuita expira a los 90 días) y la política OWASP A05/A06/A08.

## 4. Cronograma de estudio

| Día | Todos | Pista 1 (Simon) | Pista 2 (Juan Esteban) | Pista 3 (Santiago) |
|---|---|---|---|---|
| D1 mié 7 | Reunión de arquitectura: alcance y decisiones (plan §7) | Leer ADR-002, código de identidad | Leer reglas acordadas y HU-09..20 | Leer ADR-001/003, Lineamientos §7 |
| D2 jue 8 | Lecturas obligatorias (§2) | Recorridos 1–3 | Diseño de contratos (Swagger) | CI-01 y recorrido 1 |
| D3–D5 | 30 min/día: revisar el PR del lector crítico | Recorrido 4–5, TC-MFA | Recorridos 1–3 construyéndolos | Recorridos 2–4 |
| **D6 lun 12** | **Teach-back** (3 × 30 min) | Explica pista 1 | Explica pista 2 | Explica pista 3 |
| **D7 mar 13** | **Ensayo 1** (60 min, banco de preguntas aleatorio) | Corregir lagunas | Corregir lagunas | Corregir lagunas |
| **D8 mié 14** | **Ensayo 2** + demo en vivo contra Render (calentar 10 min antes) | Hoja resumen final | Hoja resumen final | Hoja resumen final |
| 15–19 oct | Ajustes tras las pruebas de Calidad; simulacro final el 19 | | | |
| **20 oct** | **Review / sustentación** | | | |

## 5. Banco de preguntas (con respuesta corta y fuente)

Las respuestas que dependen de decisiones del Sprint 2 se marcan *(según decisión final)* y se actualizan al cerrar los ADR.

### Arquitectura
1. **¿Por qué monolito modular y no microservicios?** Equipo pequeño, tiempo limitado, dominios relacionados que comparten autenticación/autorización/auditoría y un solo despliegue; los microservicios habrían añadido complejidad de despliegue, comunicación y monitoreo sin una necesidad que lo justifique. Consecuencia negativa aceptada: un solo proceso (un fallo grave afecta a todo) y la separación depende de respetar las interfaces. Revisable si aparece necesidad de escalado independiente. *(ADR-001)*
2. **¿Cómo evitan que los módulos se acoplen?** Los módulos solo se comunican por interfaces/servicios (p. ej. `UserProvisioningService`, `AuditService`); `Provider` guarda el usuario por UUID y no importa la entidad `User`; la eliminación de usuario avisa a `reservation` con un evento, no con una dependencia. *(ADR-003, plan §5.4)*
3. **¿Por qué JWT más una tabla de sesiones?** El JWT permite autenticación sin estado, pero un JWT solo no se puede revocar; la tabla guarda el `jti` para invalidar la sesión en el logout (todas las sesiones del usuario) o por expiración. Es un diseño híbrido consciente. *(ADR-002 §5, §9)*
4. **¿Por qué el token dura 1 hora y no hay renovación?** Lo exigen los criterios de HU-02/04; reduce la ventana de uso de un token robado. *(HU-02, HU-04)*
5. **¿Por qué UUID y no autonumérico?** No son enumerables al exponerlos en URL/JWT y ya son el tipo de todos los DTO y del `sub` del token; el costo es migrar si se quisiera cambiar. *(conciliación de BD)*
6. **¿Por qué Flyway y `ddl-auto: validate`?** Migraciones versionadas y reproducibles; Hibernate solo valida que el esquema coincida con las entidades y nunca lo modifica. *(application.yml, V1–V4)*
7. **¿Por qué `audit_logs` no tiene clave foránea a `users`?** Para conservar el historial al eliminar un usuario (HU-05) guardando el correo como texto; una FK con `RESTRICT` impediría borrar cualquier usuario con historial. *(conciliación de BD)*
8. **¿Cómo garantizan que no haya doble reserva?** Restricción `EXCLUDE` en PostgreSQL sobre (recurso, rango de tiempo) para reservas confirmadas; la BD rechaza el solapamiento aunque dos peticiones lleguen a la vez y la API responde 409. *(ADR-005, según decisión final)*
9. **¿Qué pasa con las reservas pasadas si se elimina un usuario o servicio?** Se conservan: la reserva guarda copias (correo, nombre del servicio y del negocio, precio) y sus FK son `SET NULL`; las futuras se cancelan con motivo `ELIMINACION_CUENTA`. *(ADR-006)*
10. **¿Por qué el precio se guarda en la reserva?** Un cambio posterior del precio del servicio no debe alterar lo ya reservado (HU-10/22).
11. **¿Qué decisiones se apartaron de los Lineamientos y por qué?** El formato de error (`timestamp/status/error/message/path/fields`) difiere de `errorCode/details/traceId`; se decidió en Sprint 1 y se propone agregar `traceId`. *(plan §7 #13)*

### Seguridad
12. **¿Cómo protegen las contraseñas?** BCrypt (hash irreversible, factor 10), política (8+, mayúscula, minúscula, carácter especial), tope de 72 **bytes** (BCrypt solo usa los primeros 72; "ñ" ocupa 2 bytes) validado en `PasswordValidator` y `@Size(max = 72)`, nunca en logs ni auditoría. *(PasswordEncoderConfig, issue #9)*
13. **¿Cómo funciona el MFA y por qué TOTP?** RFC 6238 (HMAC-SHA1, 6 dígitos, 30 s, ventana ±1) compatible con apps autenticadoras; sin SMS (débil y sin servicio de notificaciones). Obligatorio para administradores y en operaciones sensibles (Lineamientos §3.4). *(ADR-004)*
14. **¿Qué pasa si un administrador pierde su autenticador?** Hoy solo recupera el acceso un operador con acceso a la BD (borra su fila de `mfa` y se enrola de nuevo). Está previsto que otro administrador lo reinicie con su propio código, sin poder reiniciar el propio, y códigos de recuperación en Sprint 3. *(ADR-004 P10, MFA-08: pendiente)*
15. **¿Cómo se evita la fuerza bruta?** `AttemptLimiter`: 5 fallos en 15 min por clave → 429 y bloqueo de 15 min, sin siquiera comparar la contraseña. Clave = IP + correo en el login (tal como lo escribió el cliente, exista o no la cuenta) y por administrador en la confirmación de operaciones sensibles. Es atómico (`ConcurrentHashMap.compute`) y vive en memoria (una sola instancia). *(ADR-004 P7, `AttemptLimiterTest`)*
16. **¿Cómo se evita que un proveedor vea o modifique el negocio de otro?** La autorización no se limita al rol: cada servicio verifica la **pertenencia** del recurso (`isOwner`), y hay pruebas de acceso ajeno → 403. *(HU-06, `ProviderQueryService`)*
17. **¿Por qué el login responde igual si falla el correo, la contraseña o el código?** Para no permitir enumerar cuentas (OWASP A07). *(`AuthServiceImpl`)*
18. **¿Qué nunca se registra en logs o auditoría?** Contraseñas, tokens completos, secretos MFA, códigos. *(AuditLog, Lineamientos)*
19. **¿Cuál es el hallazgo OWASP más importante que encontraron?** Credenciales de demostración en un repositorio público y falta de límite de intentos en login/MFA; se corrigen con rotación, MFA obligatorio y limitador. *(plan §8)*

### Calidad y plataforma
20. **¿Cómo se mide la calidad?** Cobertura ≥ 65% (2026-10-07: 84,6% de líneas solo con unitarias y 95,2% con las de integración; era 77,1%), deuda ≤ 2 días, complejidad ciclomática < 50, severidad Minor o superior, 0 vulnerabilidades críticas, con Quality Gate que bloquea el merge. *(Rúbrica, Lineamientos §3.5)*
21. **¿Qué hace el pipeline?** Compila, corre pruebas unitarias y de integración (con PostgreSQL en contenedor), mide cobertura, ejecuta Sonar, construye la imagen y bloquea si algo falla. *(CI-01)*
22. **¿Cómo se despliega?** Render construye el `Dockerfile` multi-etapa (JDK 17 para compilar, JRE 17 para ejecutar), inyecta variables de entorno y `$PORT`, Flyway migra al arrancar y se verifica con `/actuator/health`. *(docs/guia-despliegue-render.md)*
23. **¿Por qué JDK 17 y no 24?** Con JDK 24 Lombok no genera getters/builders (el compilador falla en silencio); se fijó 17 en `pom.xml`, `Dockerfile` y guías. *(Sprint 1)*
24. **¿Qué límites tiene el despliegue gratuito?** El servicio se duerme tras 15 min (arranque ≈ 2 min) y la base gratuita expira a los 90 días (≈ 21 dic); se calienta antes de demos y hay que decidir el plan antes de esa fecha.
25. **¿Cómo prueban las reglas de negocio y la concurrencia?** Pruebas unitarias AAA por regla (un caso por escenario de los criterios), integración con Testcontainers y una prueba concurrente con barrera para el solapamiento. *(plan §9)*
26. **¿Cómo se trazó cada HU hasta la prueba?** Matriz HU → regla → API → componente → tabla → prueba generada del backlog. *(azure-boards/trazabilidad-sprint-2.md)*

### Negocio
27. **Explique el flujo completo de una reserva.** Cliente consulta catálogo (HU-13) → elige servicio → consulta horarios libres (HU-20: horario del recurso − reservas − antelación) → reserva (HU-22: confirmada automática, fin = inicio + duración, precio guardado) → la ve en "mis reservas" (HU-23) → el proveedor la ve (HU-24) → puede cancelarla (HU-25, ≥ 1 h antes).
28. **¿Qué reglas de cancelación existen?** Cliente: con ≥ 1 h de antelación; proveedor: con motivo; por eliminación de cuenta, recurso o servicio no disponible: sin regla de 1 h. Estado `CANCELADA` + motivo; nada se borra.
29. **¿Qué pasa al desactivar un recurso con reservas futuras?** Se informa cuántas se afectarían (`409 CONFIRMATION_REQUIRED`); solo si el proveedor confirma se desactiva y se cancelan con motivo `RECURSO_NO_DISPONIBLE`.
30. **¿Qué quedó fuera del sprint y por qué?** HU-07/10/11/12/15/21/27 (no bloquean el flujo de reserva); notificaciones, pagos y reportes están fuera de alcance por acuerdo. *(plan §2)*
31. **¿Qué harían distinto?** Cerrar MFA y la conciliación de BD antes de abrir módulos nuevos; habilitar Sonar desde el Sprint 1; no versionar credenciales de demostración.

### Correcciones y hallazgos del Día 1 (para explicar con el código abierto)
32. **¿Por qué el sexto intento de registro creaba la cuenta (issue #8) y cómo se evita que vuelva a pasar?** El limitador original bloqueaba con `intentos > 5`, pero la comprobación ya había pasado en el intento 6, y además consultar y contar eran dos pasos sin atomicidad. Ahora un solo estado inmutable por origen se reemplaza dentro de `compute`: los intentos 1–5 pasan, el 6.º se rechaza y fija el bloqueo. Probado con 20 hilos simultáneos (pasan exactamente 5). *(`AttemptLimiter`, `AttemptLimiterTest`, `RegistrationRateLimitIntegrationTest`)*
33. **¿Qué pasaba si dos personas se registraban a la vez con el mismo correo (issue #10)?** Ambas pasaban la verificación `existsBy…`; la segunda perdía contra la restricción única de la BD al confirmar y esa excepción caía en el manejador genérico (500). Ahora `GlobalExceptionHandler` la traduce por SQLSTATE (23505 → 409 con el mismo mensaje que si la cuenta ya existiera). La garantía real es la restricción de la BD; el código solo traduce el error. *(`RegistrationConcurrencyIntegrationTest`: sin el manejador devolvía `[500, 201]`)*
34. **¿Por qué las pruebas de integración de Sprint 1 nunca habían corrido?** Tres causas apiladas: inyectaban el `ObjectMapper` de Jackson 2 y Spring Boot 4 usa Jackson 3; Testcontainers 1.21.3 no habla con Docker Engine 29 (API 1.32 rechazada; se subió a 1.21.4); y registraban más de 5 cuentas desde una IP, así que el limitador las bloqueaba. Ahora comparten `AbstractIntegrationTest` (un solo PostgreSQL por corrida). *(`docs/resultados-pruebas-sprint-2.md`)*
35. **¿Cómo es el login en dos pasos y por qué `MFA_REQUIRED` solo aparece con una contraseña válida?** Si la contraseña es válida, la cuenta es administrativa y tiene MFA activa, pero falta `mfaCode`, se responde `401 MFA_REQUIRED` para que el cliente pida el código (no cuenta como intento fallido). Si la contraseña es inválida se responde el 401 genérico: decir "falta el código" antes revelaría que la cuenta existe y tiene MFA. *(`AuthServiceImpl`, ADR-004 P5)*
36. **¿Qué pasa si un administrador entra sin haber configurado MFA?** Puede iniciar sesión, pero `MfaEnrollmentFilter` solo le deja usar `/auth/mfa/**` y el cierre de sesión (todo lo demás: `403 MFA_ENROLLMENT_REQUIRED`); en cuanto activa la MFA, la misma sesión ya opera. Así "MFA obligatorio" deja de ser solo un evento y la cuenta no se bloquea. *(ADR-004 P4)*
37. **¿Por qué se perdían las auditorías de intentos rechazados?** Los servicios `@Transactional` auditaban el rechazo y luego lanzaban la excepción; el rollback revertía también el evento. Se corrigió con `noRollbackFor` en las operaciones que auditan antes de rechazar (no es un problema de los datos sino de dónde ocurre la excepción). Detectado al escribir `AuditPersistenceIntegrationTest`, no por Calidad. *(SP1-07)*
38. **¿Qué riesgos conocidos tiene la política de MFA actual?** Sin anti-replay (un código sirve varias veces en su ventana), secreto sin cifrar en la BD, contadores en memoria (una sola instancia), una IP de cliente que detrás del proxy de Render puede ser la del proxy, y un token robado de un administrador que aún no enroló MFA podría enrolar el autenticador del atacante. Cada uno tiene su ítem pendiente. *(ADR-004 §4)*

## 6. Guion de la sustentación (propuesta, ≈ 20 min + preguntas)

| Min | Bloque | Quién | Contenido |
|---:|---|---|---|
| 0–2 | Contexto | Simon | Problema, alcance del Sprint 2, qué se comprometió y qué quedó fuera (con la razón) |
| 2–7 | Arquitectura | Juan Esteban | Monolito modular, capas, interfaces, ADR-001/003/005/006, modelo de datos, diagramas |
| 7–13 | Demo en vivo (Render + Swagger/Postman) | Juan Esteban (reservas) y Simon (seguridad) | Preparar negocio → disponibilidad → reservar (y el 409 de doble reserva) → cancelar; login con MFA; acceso ajeno 403 |
| 13–17 | Calidad y plataforma | Santiago | Pipeline, Quality Gate, cobertura, OWASP, despliegue, diagrama de despliegue |
| 17–20 | Cierre | Santiago | Riesgos, lecciones, siguientes pasos (Sprint 3) |
| 20+ | Preguntas | Quien corresponda por pista; el lector crítico complementa | |

Plan B: si Render no responde, demo local con Docker (`docs/guia-desarrollo-local.md`) y capturas/video previos; tener el servicio **caliente** 10 min antes y credenciales de demo fuera del repo.

## 7. Checklist de preparación

- [ ] Hoja de estudio, diagrama a mano y 10 preguntas propias por persona.
- [ ] Teach-back (Día 6) y dos ensayos (Días 7 y 8) hechos y con las lagunas anotadas y cerradas.
- [ ] Demo ensayada de punta a punta con datos semilla y credenciales fuera del repo.
- [ ] ADR-004/005/006 y diagramas coherentes con el código desplegado.
- [ ] Evidencias: Quality Gate, cobertura, pipeline verde, Swagger, Postman, trazabilidad.
- [ ] Respuestas actualizadas a las decisiones finales del Sprint 2 (marcadas *(según decisión final)*).
- [ ] Fecha, formato y duración de la sustentación confirmados con el docente.
