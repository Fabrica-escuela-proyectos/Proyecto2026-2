# Resultados de pruebas — Sprint 3

Estado al **2026-11-02**. Qué se corrió, con qué resultado y qué **no** se corrió.

## 1. Suite automática (Maven, JDK 17, Docker encendido)

`./mvnw -B clean test jacoco:report` en `reservas-backend/`:

| Medida | Sprint 2 (cierre) | Sprint 3 (2026-11-02) |
|---|---|---|
| Pruebas | 658 (383 unitarias + 275 de integración) | **815 (471 unitarias + 344 de integración)**, 0 fallos |
| Cobertura de líneas (JaCoCo) | ≈ 96–97 % | **97,6 %** |
| Cobertura de ramas | ≈ 92 % | **91,7 %** |
| Endpoints | 33 | **48** (todos en Swagger: `OpenApiDocumentationIntegrationTest` en verde) |
| Grafo de módulos | sin ciclos | sin ciclos con `report` (`python docs/arquitectura/verificar_dependencias.py`) |

## 2. Pruebas nuevas por HU

| HU | Integración (PostgreSQL real) | Unitarias |
|---|---|---|
| HU-07 Editar negocio | `BusinessProfileIntegrationTest` (14: edición completa y parcial, borrar opcionales, nombre vacío, 5 teléfonos y 2 tipos inválidos del AC, otro proveedor, cliente, sin sesión, registro de proveedor tras V13) | `BusinessProfileServiceImplTest`, `CatalogServiceImplTest` |
| HU-10/11/12 Servicios | `ServiceEditAndLifecycleIntegrationTest` (12: precio sin afectar reservas, precio 0, validaciones, nombre repetido, accesos, desactivar sin/con reservas y con confirmación, **carrera 4 reservas vs. desactivación**, reactivar sin restaurar canceladas, idempotencia) | `ServiceEditAndLifecycleServiceTest`, `ServiceDeactivationServiceImplTest` |
| HU-15 Editar recurso | `ResourceEditIntegrationTest` (6) | `ResourceClosureServiceImplTest` (parte HU-15) |
| HU-21 Cierres | `ResourceClosureIntegrationTest` (15: cierre parcial y de día completo, horario habitual intacto, confirmación, cancelación solo de las afectadas, reserva dentro del cierre, ejemplos inválidos del AC, fecha pasada, traslape y borde compartido, **dos cierres simultáneos `[201, 409]`**, otros recursos intactos, accesos, listar y eliminar, no eliminar uno empezado) | `ResourceClosureServiceImplTest`, `ResourceClosureBookingServiceImplTest`, `ResourceScheduleLookupImplTest` (resta de cierres) |
| HU-27 Completar | `BookingCompletionIntegrationTest` (5) | `BookingCompletionServiceImplTest` (6) |
| HU-29..34 Reportes | `ReportIntegrationTest` (8, con números exactos: 3600 min disponibles, 180 reservados = 5,0 %; cierre que resta 720 min; conteos por estado, valor, ranking con nombre vigente; vacíos con mensaje; periodo por defecto; 400 por parámetros; 403/401; resumen del administrador) | `ReportServiceImplTest` (16) |

## 3. Postman / Newman

Contra la aplicación **local** (perfil dev, PostgreSQL 16 temporal en Docker, V1–V14 desde cero), sin administrador: **119 peticiones, 166 aserciones, 0 fallos**. Colección: [`docs/postman/`](postman/README.md) (131 peticiones; la carpeta 07b es la del Sprint 3).

## 4. Lo que NO se ha corrido todavía

* **Nada del Sprint 3 contra Render**: el código no está commiteado ni desplegado. Tras el despliegue: correr la colección contra Render (`--timeout-request 150000`) y verificar que Flyway aplicó V13 y V14 en Neon (`select version from flyway_schema_history order by installed_rank desc limit 2;`).
* La carpeta 08 de Postman con administrador (incluido HU-34) en esta versión.
* El análisis de Sonar del código nuevo (corre en el pipeline al hacer push).
* Pruebas de Calidad (17-23 nov) con sus casos `CP-*`.
