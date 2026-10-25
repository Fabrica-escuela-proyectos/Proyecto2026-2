# Conexiones — Diagrama de despliegue

Figura: [`diagrama-despliegue.png`](diagrama-despliegue.png) (fuente editable: [`diagrama-despliegue.svg`](diagrama-despliegue.svg); se abre con un navegador, Inkscape o draw.io). Estado a **2026-10-12**.

## 1. Nodos y artefactos

| Nodo | Estereotipo | Qué ejecuta o contiene | Evidencia |
|---|---|---|---|
| Cliente | «device» | Navegador (Swagger UI), Postman/Newman de Calidad; frontend futuro (no existe) | [`docs/postman/`](../../postman/README.md), [guía de Swagger](../../api/guia-swagger-openapi.md) |
| Proxy inverso de Render | parte de «cloud» | Termina TLS, redirige HTTP→HTTPS, añade `X-Forwarded-For`/`-Proto` | Sondeos de [OWASP §4](../../seguridad/owasp-top10-sprint-2.md) (`http://` → `301`) |
| Web Service de Render | «execution environment» | Contenedor Docker (`eclipse-temurin:17-jre`, usuario `app` sin root, `HEALTHCHECK`) | [`Dockerfile`](../../../reservas-backend/Dockerfile), [guía de despliegue](../../guia-despliegue-render.md) |
| `reservas-backend.jar` | «artifact» | Spring Boot 4.1.1 / Java 17, monolito modular: `identity`, `provider`, `service`, `resource`, `reservation`, `audit`, `common` | [ADR-001](../adr/ADR-001-monolito-modular.md), [ADR-007](../adr/ADR-007-comunicacion-entre-modulos-y-eventos.md), `python docs/arquitectura/verificar_dependencias.py` |
| PostgreSQL 16 | «database» | Esquema `public` con 12 migraciones Flyway (V1–V12); restricción `EXCLUDE` anti-overbooking (extensión `btree_gist`) | [`db/migration/`](../../../reservas-backend/src/main/resources/db/migration), [ADR-005](../adr/ADR-005-anti-overbooking-y-concurrencia.md) |
| GitHub | «cloud» | Repositorio público (`main`), Dependabot | [`.github/dependabot.yml`](../../../.github/dependabot.yml) |
| GitHub Actions | «execution environment» | `ubuntu-latest`: pruebas, Sonar, build y despliegue | [`.github/workflows/build.yml`](../../../.github/workflows/build.yml) |
| SonarCloud | «cloud» | Quality Gate, cobertura (JaCoCo) y vulnerabilidades del código propio | Proyecto `Fabrica-escuela-proyectos_Proyecto2026-2` |
| Equipo de desarrollo | «device» | `./mvnw spring-boot:run` (perfil `dev`) y PostgreSQL de `docker-compose`; las pruebas usan Testcontainers | [guía local](../../guia-desarrollo-local.md) |

## 2. Rutas de comunicación

| De | A | Protocolo / mecanismo | Qué viaja | Seguridad |
|---|---|---|---|---|
| Cliente | Proxy de Render | HTTPS 443 | Peticiones REST con JSON | TLS de Render; JWT en `Authorization: Bearer`; `X-MFA-Code` en operaciones sensibles |
| Proxy | Contenedor | HTTP al puerto `$PORT` (red interna de Render) | La misma petición + `X-Forwarded-For` | La IP real del cliente se toma de `X-Forwarded-For`, de derecha a izquierda, saltando los proxies de confianza (privados y **Cloudflare**, que está delante de Render); un cliente no puede falsear su IP. Verificado en Render el 2026-10-12 (guardaba la IP de Cloudflare) y reverificado el 2026-10-13 tras la corrección (guarda la IP real); ver OWASP A04 |
| Contenedor | PostgreSQL | JDBC | Consultas y migraciones Flyway al arrancar | Credenciales por variables de entorno (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`); nunca en el repositorio |
| GitHub Actions | Render | HTTPS `POST` al *deploy hook* | Orden de desplegar (solo en `main`) | URL secreta en `RENDER_DEPLOY_HOOK_URL` (GitHub Secrets) |
| GitHub Actions | Render | HTTPS `GET /actuator/health` | *Smoke test* tras el despliegue (hasta 10 min) | Ruta pública sin datos sensibles |
| Render | GitHub | HTTPS (clon del repositorio) | Código para construir la imagen con el `Dockerfile` (Auto-Deploy apagado; solo construye cuando llega el hook) | Repositorio público |
| GitHub Actions | SonarCloud | HTTPS | Análisis y reporte de cobertura | `SONAR_TOKEN` (secreto de Actions **y** de Dependabot) |
| Dependabot | GitHub | Pull requests | Actualizaciones semanales (Maven, Actions, Docker) | Cada PR pasa por el mismo pipeline |

## 3. Variables de entorno del despliegue (solo nombres; los valores nunca van al repositorio)

| Variable | Obligatoria | Efecto |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Recomendada (`prod`) | `prod` activa los logs JSON con `traceId`. Sin perfil la aplicación arranca igual con la configuración base |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Sí | Conexión a PostgreSQL |
| `JWT_SECRET` | Sí | Firma de los tokens (≥ 32 caracteres; el arranque falla si falta o es la clave de desarrollo) |
| `SWAGGER_ENABLED` | No | `true` publica `/swagger-ui.html` y `/v3/api-docs` (hoy **activa en Render** por decisión del equipo, ver S-30) |
| `CORS_ALLOWED_ORIGINS`, `MAX_REQUEST_BODY_BYTES`, `RATE_LIMIT_*` | No | Orígenes del frontend, tope de cuerpo (64 KB) y límites de intentos |
| `BOOTSTRAP_ADMIN_*` | No | Crea el primer administrador (quitar la contraseña después) |

## 4. Decisiones y límites del despliegue (para justificar)

| Decisión | Razón | Costo / límite conocido |
|---|---|---|
| **Una sola instancia** en el plan gratuito de Render | Es lo que cubre el proyecto académico sin costo; el monolito modular no necesita orquestación (ADR-001) | Los límites de intentos viven en memoria; el servicio se duerme a los 15 min y tarda ≈ 2 min en despertar; **el plan gratuito tiene 750 h/mes por espacio de trabajo y la base expira a los 30 días: se agotó el 2026-10-24** (ver [`guia-recuperar-despliegue.md`](../../guia-recuperar-despliegue.md)) |
| **Contenedor Docker** con construcción en dos etapas | Mismo artefacto en local, CI y Render; el JDK 17 es obligatorio (con 24 Lombok no genera código) | Imagen de ≈ 560 MB (JRE completo) |
| **Despliegue por *deploy hook* desde Actions** (Auto-Deploy apagado) | Solo se despliega lo que pasó pruebas, Sonar y build | El pipeline completo tarda unos 5 minutos |
| **Usuario sin privilegios + `HEALTHCHECK`** | Reduce el impacto de una vulnerabilidad (OWASP A05) | — |
| **Swagger encendido en Render** | Decisión de Simon (2026-10-12): Calidad y el resto del equipo prueban la API sin Postman | La documentación expone la lista de rutas (riesgo bajo, la API ya está documentada en un repositorio público); se apaga quitando `SWAGGER_ENABLED` |
| **Contenedores y orquestación** (Kubernetes, varios nodos) | Quedan para el Sprint 3 según el entregable del curso | — |
