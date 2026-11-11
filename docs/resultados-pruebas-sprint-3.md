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

## 3. Sonar

SonarCloud (análisis del commit `6dab10a`, 2026-11-03): **Quality Gate OK**, 0 bugs, 0 vulnerabilidades, 0 *code smells*, deuda técnica 0 min, calificaciones A en fiabilidad, seguridad y mantenibilidad, 100 % de *hotspots* revisados, cobertura 96,1 % (líneas 97,6 %, ramas 91,8 %), duplicación 0,9 %, 8.021 líneas de código. El primer análisis del Sprint 3 (`b311731`) dio el gate en **ERROR** por fiabilidad (D): 7 issues S8696 en dos pruebas unitarias (comparación con `==` dentro de Mockito) y uno S2201 antiguo en `MfaServiceImpl`; corregidos en `6dab10a`.

## 4. Postman / Newman

**Contra Render (2026-11-03, código del Sprint 3 desplegado por el pipeline #71, base en Neon), sin administrador: 119 peticiones, 166 aserciones, 0 fallos** (1 min 15 s; corrida `989ty7xb`). Quedaron en la base los usuarios de prueba `qa.*.989ty7xb@example.com` (dos proveedores y un cliente) con su negocio y reservas; se borran con un administrador (`DELETE /api/v1/users/{id}` + `X-MFA-Code`) o corriendo la carpeta 08.

Antes, contra la aplicación **local** (perfil dev, PostgreSQL 16 temporal en Docker, V1–V14 desde cero), sin administrador: **119 peticiones, 166 aserciones, 0 fallos**. Colección: [`docs/postman/`](postman/README.md) (131 peticiones; la carpeta 07b es la del Sprint 3).

## 5. Cambios por las respuestas de Calidad (2026-11-11)

Suite completa tras P12 (cliente eliminado) y P5 (correo): **833 pruebas en verde** (483 unitarias + 350 de integración), cobertura **97,5 % líneas / 91,1 % ramas** (`notification`: 94,7 % de líneas). Nuevas: `CancellationEmailIntegrationTest` (6: el cliente recibe el correo al cancelar el proveedor, al desactivar el servicio, al cerrar el recurso solo si la reserva cae en el cierre y al eliminar al proveedor; **no** recibe correo si la operación no se confirma (409) ni cuando él mismo cancela), `CancellationNotifierTest` (4), `EmailSendersTest` (6, Brevo contra un servidor HTTP local falso: formato del pedido, clave en la cabecera, error sin filtrar la clave, dominios de prueba omitidos, elección del emisor) y casos nuevos en `BookingBulkCancellationServiceImplTest`, `AccountDeletionBookingListenerTest` y `AccountDeletionIntegrationTest` («Cliente eliminado» y sin correo en reservas pasadas, completadas y canceladas). **No verificado:** un envío real por Brevo (requiere la clave del equipo) ni nada de esto en Render.

## 6. Lo que NO se ha corrido todavía

* La consulta directa a Neon de `flyway_schema_history` (V13 y V14): la hace Simon en el SQL Editor. Indirectamente ya está comprobado: la aplicación arranca con `ddl-auto: validate` y el Swagger de Render expone los 48 endpoints.
* La carpeta 08 de Postman con administrador (incluido HU-34) en esta versión.
* Pruebas de Calidad (17-23 nov) con sus casos `CP-*`.
