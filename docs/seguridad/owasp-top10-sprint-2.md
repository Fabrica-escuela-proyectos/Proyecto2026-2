# Revisión OWASP Top 10 — Sprint 2

**Alcance:** backend del repositorio al 2026-10-10 (HU-01 a HU-28 salvo Tier 3) y su despliegue en Render. **Versión de la lista:** OWASP Top 10 **2021** (A01–A10), la que usan el plan del sprint y los Lineamientos; si el docente trabaja con una edición más reciente, las categorías se reordenan pero los hallazgos de abajo siguen siendo válidos.
**Tarea:** `OWASP-01` (matriz contra el código, hallazgos y correcciones). Reemplaza y completa la plantilla de [plan §8](../sprint-2/plan-de-trabajo-sprint-2.md).

## 1. Cómo se hizo (y qué NO es esta revisión)

| Se hizo | Evidencia |
|---|---|
| Lectura del código de seguridad (`SecurityConfig`, filtros JWT y MFA, `GlobalExceptionHandler`, servicios de propiedad) y de la configuración | Referencias de archivo en la matriz |
| Revisión de las **pruebas automatizadas de seguridad**: 21 clases de prueba comprueban `401`/`403`, incluidas las de pertenencia (IDOR) por cada HU de gestión | `src/test/…`; suite completa 614 pruebas en verde |
| **Sondeos de solo lectura al servicio en Render** (2026-10-10): cabeceras, redirección HTTP→HTTPS, endpoints de Actuator, CORS, forma de los errores | Sección 4 |
| Ejecución de la colección de [Postman](../postman/README.md) contra Render (88 peticiones, 119 aserciones, 0 fallos), que incluye accesos ajenos y MFA | [resultados](../resultados-pruebas-sprint-2.md) |

**No es** un pentest: no se probó fuerza bruta real contra Render (para no bloquear al administrador), no se corrió un escáner de vulnerabilidades (ZAP, dependency-check) ni se auditó el sistema operativo/red de Render. Lo que depende de eso figura como «por verificar».

## 2. Resumen

| Riesgo | Estado | Hallazgo principal |
|---|---|---|
| A01 Control de acceso | **Cubierto** (con una decisión consciente) | Pertenencia validada en servicio para cada recurso; HU-20 es pública a propósito |
| A02 Fallas criptográficas | **Parcial** | Secreto TOTP en claro en la BD; contraseña del admin demo publicada (SEC-01) |
| A03 Inyección | **Cubierto** | Consultas parametrizadas y validación con límites; falta prueba con cargas de ataque |
| A04 Diseño inseguro | **Parcial** | Buen diseño transaccional; **límites por IP posiblemente compartidos detrás del proxy (OWASP-03)** y sin límite de tamaño de cuerpo |
| A05 Configuración insegura | **Parcial** | Perfil `dev` por defecto, contenedor como `root`, faltan cabeceras (Referrer/Permissions) |
| A06 Componentes vulnerables | **Brecha** | Sin escaneo automático de dependencias; `jjwt` 0.12.5 (hay 0.13.0) |
| A07 Autenticación | **Parcial** | Fuerte (BCrypt, MFA admin, límites); sin cambio de contraseña, sin anti-replay, contraseña demo pública |
| A08 Integridad | **Parcial** | CI y migraciones inmutables; `main` sin protección verificada, commits subidos por la web |
| A09 Registro y monitoreo | **Parcial** | Auditoría sin secretos; sin `traceId`, sin logs JSON ni alertas |
| A10 SSRF | **No aplica** | El backend no hace llamadas salientes |

**Prioridad de corrección antes de la entrega** (de mayor a menor): ① rotar la contraseña del admin demo y quitarla de la guía; ② `server.forward-headers-strategy` (OWASP-03); ③ perfil por defecto y secreto JWT: fallar si falta la configuración; ④ Dependabot / escaneo de dependencias; ⑤ proteger `main`; ⑥ cabeceras y límite de tamaño de cuerpo (OWASP-02).

## 3. Matriz detallada

### A01 — Control de acceso roto · **Cubierto**

| | |
|---|---|
| **Qué hay** | 33 endpoints con su acceso en [`referencia-api-sprint-2.md`](../api/referencia-api-sprint-2.md): 4 públicos, 8 de cualquier usuario autenticado, 15 de proveedor, 4 de cliente, 2 de administrador. Todo lo no listado como público exige token (`anyRequest().authenticated()`). `@PreAuthorize` por rol en cada método. |
| **Pertenencia (anti-IDOR)** | `BusinessAccessService.requireOwner` comprueba que el negocio sea del proveedor en negocio, servicios, recursos, horarios, asignaciones, reservas del negocio y cancelación del proveedor; los clientes solo ven y cancelan **sus** reservas (`/users/{otro}/bookings` → 403, mismo código exista o no el otro usuario). Ni un ADMINISTRADOR gestiona el catálogo ajeno. |
| **Sesión** | El token JWT se cruza con la tabla `sessions` en cada petición: logout y expiración revocan de verdad (`JwtAuthenticationFilter`). |
| **Pruebas** | Cada HU de gestión tiene pruebas de integración de «proveedor ajeno → 403», «cliente → 403» y «sin sesión → 401»; la colección de Postman las repite contra Render (carpetas 03, 05–07). |
| **Decisiones** | HU-20 (disponibilidad) es **pública** por su criterio de aceptación: expone horarios libres y los nombres/ids de los recursos de un servicio. Se acepta como riesgo bajo (son datos de oferta, no personales). HU-24 muestra al proveedor id, nombre y correo del cliente de **sus** reservas (decisión del equipo; dato personal). |
| **Brecha** | No existe una prueba única que recorra los 33 endpoints y verifique su rol (la matriz se genera del código, pero no se «ejecuta»). Acción propuesta: prueba parametrizada que pida cada ruta con los 3 roles y compare con la matriz. |

### A02 — Fallas criptográficas · **Parcial**

| | |
|---|---|
| **Qué hay** | Contraseñas con **BCrypt** (`BCryptPasswordEncoder` por defecto, costo 10) y política de 8+ con mayúscula, minúscula y especial; límite de 72 bytes (el de BCrypt) validado. **JWT HS256** de 1 h; la clave se exige de al menos 256 bits (jjwt lanza `WeakKeyException` y la aplicación no arranca si es corta o falta). **HTTPS** en Render: `http://` responde `301` a `https://` y la respuesta trae `Strict-Transport-Security` (sondeo). Nunca se registran contraseñas, tokens ni secretos TOTP en logs ni auditoría. |
| **Brechas** | ① El secreto TOTP está **en claro** en `mfa.secret` (ADR-004 P9, `MFA-07`; solo se muestra una vez en `/setup`). ② La contraseña del administrador demo en Render **es la publicada** en `docs/guia-prueba-aplicacion-desplegada.md` (**SEC-01/02**, confirmado el 2026-10-10 al ejecutar las pruebas). El MFA obligatorio lo mitiga. ③ Un único `JWT_SECRET` para todo (sin rotación ni `kid`). ④ Por verificar: que la conexión a la base de Render use TLS (`sslmode` en `DB_URL`). ⑤ La contraseña de la base anterior (Supabase) quedó **expuesta en el historial de git** (commit `5ae30bd`): hay que confirmar que fue rotada (**SEC-02**). |
| **Acción** | Rotar y quitar la contraseña de la guía (SEC-01) y confirmar la rotación de la contraseña expuesta en el historial (SEC-02); cifrar el secreto TOTP con AES-GCM y clave de entorno (MFA-07); revisar `DB_URL`. |

### A03 — Inyección · **Cubierto**

| | |
|---|---|
| **Qué hay** | Todo acceso a datos es **JPA/JPQL con parámetros** o `Specification` (filtros de HU-24); la única consulta nativa del proyecto (`ResourceRepository.lockAndReadActive`) usa el parámetro con nombre `:id`, sin concatenar. Entrada validada con Bean Validation con **límites de longitud** en todos los campos de texto (corrección del bug #9) y formato estricto de fechas y horas. La API solo habla JSON (415 si no), no renderiza HTML: no hay superficie de XSS reflejado ni almacenado. Los identificadores de ruta son `UUID` tipados (un valor inválido es 400, no llega a la base). |
| **Brecha** | No hay una prueba con cargas típicas (`' OR 1=1 --`, `<script>`, nombres de 10 000 caracteres) que lo demuestre; la protección es estructural, no probada. |
| **Acción** | Una prueba de integración con cargas de ataque en los campos de texto de HU-09/14/22 (esperado: 201 con el texto guardado literal, o 400; nunca 500). |

### A04 — Diseño inseguro · **Parcial**

| | |
|---|---|
| **Qué hay** | Reglas de negocio aplicadas **en el servidor**, no en el cliente: el precio de la reserva sale del servicio (no del cuerpo), la duración debe coincidir con la del servicio, el cliente sale de la sesión, el negocio sale de la ruta. **Anti-overbooking en la base de datos** (restricción `EXCLUDE`), bloqueo de fila en las operaciones que compiten (cancelar, desactivar, asignar recursos, editar horarios) y transacciones atómicas; una carrera real entre reservar y desactivar se detectó y corrigió con prueba repetida. Límites de intentos: registro (5 por IP en 10 min) y login/MFA (5 fallos en 15 min) → `429`. Operaciones sensibles con confirmación MFA. Paginación con tope (50). |
| **Brechas** | ① **`OWASP-03` (por verificar, riesgo alto):** el límite por IP usa `request.getRemoteAddr()` y la aplicación **no configura `server.forward-headers-strategy`**; detrás del proxy de Render es probable que todas las peticiones lleguen con la IP del proxy, de modo que el «límite por IP» sería **global** (5 registros cada 10 minutos para *todos* los usuarios) y la auditoría registraría la IP equivocada. No se forzó la prueba en Render para no bloquear el registro. ② Sin límite en otros endpoints (crear reservas, la consulta pública de HU-20) ni de tamaño de cuerpo. ③ Los límites viven en memoria: se pierden al reiniciar y no se comparten entre instancias (una sola instancia hoy). |
| **Acción** | `server.forward-headers-strategy: native` (ya en el plan como OWASP-03) y comprobar con la tabla `audit_logs`; límite de tamaño de cuerpo y de tasa en rutas públicas (OWASP-02). |

### A05 — Configuración de seguridad incorrecta · **Parcial**

| | |
|---|---|
| **Qué hay** | **Actuator:** solo `health` e `info` expuestos; en Render solo `health` responde sin sesión y `/actuator`, `/env`, `/beans`, `/mappings`, `/info` dan `401` (sondeo). **Errores** sin traza: el `500` devuelve un mensaje genérico y el detalle va solo al log (`GlobalExceptionHandler`); una ruta inexistente o un UUID mal formado dan JSON uniforme (sondeo). **Cabeceras** en las respuestas de Render: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-store`, `Strict-Transport-Security`. **CORS** restringido: sin configuración, una petición `OPTIONS` con `Origin` ajeno no recibe `Access-Control-Allow-*` (sondeo). CSRF desactivado a propósito (API sin estado con JWT en cabecera, sin cookies; ADR-002 §9; Sonar `S4502` marcado como seguro). **Swagger** (aplicado el 2026-10-10, ARQ-01) está **apagado por defecto**: solo se publica con `SWAGGER_ENABLED=true` (perfiles `dev` y `test` lo encienden); con el interruptor apagado `/v3/api-docs` y `/swagger-ui` responden `404` (prueba `OpenApiDisabledIntegrationTest`). En Render (`prod`) queda apagado hasta que se active la variable para una demostración. Ver [la guía](../api/guia-swagger-openapi.md). |
| **Brechas** | ① **Perfil por defecto = `dev`** (`SPRING_PROFILES_ACTIVE:dev`): si un despliegue olvida la variable, arranca con `show-sql`, logs `DEBUG`, contraseña de base de datos y **clave JWT de desarrollo públicas** en el repositorio, **y desde ARQ-01 también con Swagger encendido** (el perfil `dev` lo habilita). En Render está en `prod` (guía de despliegue) y, aun con la clave conocida, un token falso no pasaría porque se cruza con la tabla `sessions`; pero conviene que **falle al arrancar** en vez de caer a `dev`. ② El contenedor corre como **`root`** (`Dockerfile` sin `USER`). ③ Faltan `Referrer-Policy` y `Permissions-Policy`; no hay `Content-Security-Policy` (irrelevante en una API JSON, relevante si se sirve Swagger). ④ Sin límite explícito de tamaño de cuerpo. ⑤ Imágenes base sin versión fija de parche. |
| **Acción** | Quitar el valor por defecto del perfil y de las claves de desarrollo fuera del perfil `dev`; `USER` no root y `HEALTHCHECK` en el `Dockerfile`; cabeceras y límites (OWASP-02); silenciar el aviso «Using generated security password» (SEC-03). |

### A06 — Componentes vulnerables y desactualizados · **Brecha**

| | |
|---|---|
| **Qué hay** | Spring Boot **4.1.1** (la última 4.1.x en Maven Central a 2026-10-10), Testcontainers 1.21.4, JaCoCo 0.8.12, Java 17 (LTS). SonarCloud analiza el código propio (vulnerabilidades: 0 tras corregir). |
| **Brecha** | **Ninguna herramienta revisa las dependencias contra bases de vulnerabilidades** (ni Dependabot ni OWASP Dependency-Check en el pipeline). `jjwt` está en **0.12.5** y existe 0.13.0 (si trae correcciones de seguridad no se sabe sin revisar sus notas). Las acciones de GitHub están fijadas por etiqueta (`@v5`), no por SHA. |
| **Acción** | Añadir `.github/dependabot.yml` (Maven + GitHub Actions + Docker, semanal) y, opcionalmente, un job con `dependency-check` o el escaneo de dependencias de GitHub (`CI-06`). Revisar la subida de `jjwt`. |

### A07 — Fallas de identificación y autenticación · **Parcial**

| | |
|---|---|
| **Qué hay** | Política de contraseña; mensajes de login **genéricos** (no revelan si el correo existe ni si la cuenta tiene MFA); límite de fuerza bruta con bloqueo de 15 min y sin comprobar siquiera la contraseña mientras dure; tokens de 1 h con sesión revocable (logout efectivo); **MFA TOTP obligatorio para administradores** (login en dos pasos, enrolamiento forzoso, confirmación en operaciones sensibles) verificado contra los vectores de la RFC 6238 y contra Render. |
| **Brechas** | ① Contraseña demo pública (A02). ② No hay endpoint de **cambio de contraseña** (`SP1-01`). ③ Un código TOTP puede **reutilizarse** dentro de su ventana (anti-replay pendiente, `MFA-06`, ADR-004 P8). ④ Sin recuperación de MFA ni reinicio por otro administrador (`MFA-08`). ⑤ El registro revela si un correo existe (`409`): lo exige el criterio de HU-01; el login no lo revela. ⑥ Con un token robado de un administrador **sin** MFA se podría enrolar el autenticador del atacante: `POST /auth/mfa/setup` no pide la contraseña (**SEC-04**; mitigado enrolando al admin apenas se crea). ⑦ ADR-004 sigue **sin aprobar** por los tres de Arquisoft. |

### A08 — Fallas de integridad de software y datos · **Parcial**

| | |
|---|---|
| **Qué hay** | Pipeline con pruebas, Sonar y build antes de desplegar; el despliegue lo dispara un *deploy hook* secreto. **Flyway** valida el checksum de las migraciones: una migración ya aplicada no puede alterarse sin que falle el arranque. `ddl-auto: validate` impide que Hibernate modifique el esquema. Dependencias desde Maven Central (verificación de sumas por defecto). Jackson sin tipado polimórfico (no hay deserialización insegura). |
| **Brechas** | ① No se verificó que `main` tenga **protección de rama** (PR obligatorio y revisión); se ha commiteado directo a `main` y hay commits «Add files via upload» (`cac39f9`, `f92455f`), práctica que `CLAUDE.md` prohíbe porque ya rompió `main` una vez. ② Acciones fijadas por etiqueta y no por SHA. ③ Sin firma de artefactos ni SBOM. |
| **Acción** | Activar protección de `main` (PR + checks requeridos), `CODEOWNERS`, fijar acciones por SHA (decisión del equipo; coste de mantenimiento). |

### A09 — Fallas de registro y monitoreo · **Parcial**

| | |
|---|---|
| **Qué hay** | Tabla `audit_logs` con **19 tipos de evento** (registro, login y rechazos, MFA, cambio de rol, eliminación, y en el Sprint 2 creación/cancelación de reservas, configuración de negocio, recursos, disponibilidad) con resultado, IP de origen y **sin contraseñas, tokens ni secretos** (probado). Los rechazos se persisten aunque la operación falle (`noRollbackFor`). Los errores internos se registran con traza en el servidor. |
| **Brechas** | Sin `traceId` de correlación en errores y logs (`API-03`); logs en texto, no JSON; sin alertas ante `429`, `403` repetidos o fallos de MFA; no hay endpoint ni vista para que un administrador consulte la auditoría; la retención depende de Render. |

### A10 — Falsificación de peticiones del lado del servidor (SSRF) · **No aplica**

El backend **no hace ninguna llamada HTTP saliente** (ningún `RestTemplate`, `WebClient`, `RestClient` ni `HttpClient` en el código; verificado por búsqueda). No hay funciones que reciban URLs del usuario. Revisar de nuevo si se añaden notificaciones por correo/webhook (hoy fuera de alcance).

## 4. Sondeos a Render (2026-10-10, solo lectura)

| Prueba | Resultado |
|---|---|
| `GET /actuator/health` (cabeceras) | `200`; `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 0`, `Strict-Transport-Security: max-age=31536000; includeSubDomains` |
| `GET /actuator`, `/actuator/env`, `/actuator/beans`, `/actuator/mappings`, `/actuator/info` | `401` (no expuestos sin sesión) |
| `GET /v3/api-docs`, `/swagger-ui.html` | `401` el 2026-10-10, **antes** de aplicar Swagger. Con el cambio de ARQ-01 desplegado y `SWAGGER_ENABLED` sin definir se espera `404` (por verificar tras el despliegue) |
| `http://…/actuator/health` | `301` a `https://` |
| `OPTIONS /api/v1/auth/login` con `Origin: https://evil.example` | `401` sin cabeceras `Access-Control-*` |
| `GET /api/v1/inexistente` con un token basura | `401` en formato `ApiError` (no revela rutas ni traza) |
| `GET /api/v1/services/no-es-uuid/availability` | `400` `VALIDATION_ERROR` con mensaje controlado |

## 5. Plan de cierre

| # | Acción | Riesgo | Tarea | Esfuerzo | Responsable sugerido |
|---|---|---|---|---:|---|
| 1 | Rotar la contraseña del admin demo y quitarla de `docs/`; confirmar la rotación de la contraseña de Supabase del historial | A02/A07 | SEC-01, SEC-02 | 1 h | Simon |
| 2 | `server.forward-headers-strategy: native` y verificar la IP en `audit_logs` | A04/A09 | OWASP-03 | 1 h | Simon |
| 3 | Sin perfil `dev` por defecto; fallar si falta `JWT_SECRET` fuera de `dev`; `USER` no root | A05 | **SEC-05 (nueva)** | 2 h | Juan Esteban |
| 4 | `dependabot.yml` + revisar `jjwt` 0.13.0 | A06 | CI-06 | 2 h | Santiago |
| 5 | Protección de `main`, `CODEOWNERS` | A08 | CI-05 | 1 h | Santiago |
| 6 | Cabeceras (Referrer/Permissions), límite de tamaño de cuerpo y de tasa en rutas públicas | A04/A05 | OWASP-02 | 3 h | Juan Esteban |
| 7 | Prueba de cargas de ataque (A03) y prueba de la matriz de acceso completa (A01) | A01/A03 | OWASP-01 | 3 h | Simon |
| 8 | Cifrar el secreto TOTP; anti-replay; cambio de contraseña; pedir la contraseña en `/mfa/setup` | A02/A07 | MFA-06/07, SP1-01, SEC-04 | 9 h | Simon |
| 9 | `traceId` y logs JSON | A09 | API-03 | 3 h | Juan Esteban |

Aprobación de [ADR-004](../arquitectura/adr/ADR-004-politica-mfa.md) pendiente de los tres integrantes de Arquisoft.
