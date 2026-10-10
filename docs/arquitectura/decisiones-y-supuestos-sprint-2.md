# Decisiones y supuestos del Sprint 2

Registro de **todo lo que se decidió o se supuso** durante el desarrollo del Sprint 2 mientras Calidad, el docente u otro equipo no digan lo contrario. Sirve para tres cosas: (1) la sustentación («¿por qué lo hicieron así?»), (2) saber **a quién preguntar** y qué cambiaría según la respuesta, (3) que quien retome el proyecto no tenga que redescubrirlo.

* **Decidida** = la tomó el equipo o Simon de forma explícita.
* **Supuesto** = la tomó Claude/Simon por defecto porque el criterio de aceptación no la fijaba o era ambiguo; **espera confirmación**.
* «Impacto» estima el costo de cambiarla, **no** el riesgo de equivocarse.

## 1. Decisiones de arquitectura (ADR)

| ADR | Tema | Estado |
|---|---|---|
| [001](adr/ADR-001-monolito-modular.md) | Monolito modular | Sprint 1 |
| [002](adr/ADR-002-autenticacion-y-sesiones.md) | Autenticación y sesiones (JWT + tabla `sessions`) | Sprint 1 |
| [003](adr/ADR-003-modularidad-e-interfaces.md) | Módulos e interfaces | Sprint 1 |
| [004](adr/ADR-004-politica-mfa.md) | Política de MFA (TOTP obligatorio para administradores) | **Propuesto**; implementado; **falta la aprobación de Juan Esteban y Santiago** |
| [005](adr/ADR-005-anti-overbooking-y-concurrencia.md) | Anti-overbooking en la base y bloqueos de fila | Propuesto; implementado y probado |
| [006](adr/ADR-006-historial-snapshots-y-cancelaciones.md) | Historial, copias y modelo de cancelación (estado + origen) | Propuesto; implementado y probado |
| [007](adr/ADR-007-comunicacion-entre-modulos-y-eventos.md) | Interfaces entre módulos, orquestación y evento de eliminación | Propuesto; implementado y probado |

Otros documentos de decisión: [`convenciones-bd.md`](../bd/convenciones-bd.md) (propuesta `BD-01`, falta acordarla con BD), [plan §7](../sprint-2/plan-de-trabajo-sprint-2.md) (decisiones abiertas originales) y [OWASP](../seguridad/owasp-top10-sprint-2.md).

## 2. Registro de supuestos y decisiones de producto/API

Quién confirma: **Calidad** (casos `CP-*` e issues), **PO/docente** (alcance y reglas de negocio), **BD** (Andraus), **Arquisoft** (los tres integrantes).

### A. Alcance y proceso

| ID | Tema | Qué se decidió o supuso | Origen | Alternativa | Impacto si cambia | Confirma |
|---|---|---|---|---|---|---|
| S-01 | Alcance del sprint | Tier 1 (HU-08, 09, 13, 14, 18, 19, 20, 22, 23, 24, 25) y Tier 2 (HU-16, 17, 26, 28) implementados. **Tier 3 fuera:** HU-07, 10, 11, 12, 15, 21, 27 | Decidida (Simon, 2026-10-08, HU-08 sube a Tier 1) | Hacer también el Tier 3 | Cada HU de Tier 3 son ~1 día; HU-21 y HU-27 dependen de que se confirme su alcance | PO/docente |
| S-02 | Notificaciones | **Fuera de alcance.** El «aviso» que piden HU-25/26/28 queda como evento de auditoría (`CANCELACION_RESERVA`) | Supuesto (plan dec. 5) | Correo/webhook | Nuevo módulo de salida (y revisar OWASP A10) | Calidad/PO |
| S-03 | Estados de cancelación | Estado `CANCELADA` + **origen** (CLIENTE, PROVEEDOR, ELIMINACION_CUENTA, RECURSO_NO_DISPONIBLE, SERVICIO_NO_DISPONIBLE) en vez de «Cancelada por proveedor» como estado (ADR-006) | Decidida (plan dec. 3) | Estados compuestos | Migración + respuestas; Calidad podría esperar el texto literal del estado | Calidad |
| S-04 | Acceso a la consulta | **HU-13 (catálogo) exige sesión; HU-20 (disponibilidad) es pública** | Decidida (Simon, 2026-10-08, tras ver el escenario «usuario sin sesión») | Ambas públicas o ambas con sesión | 1 línea en `SecurityConfig` + pruebas | Calidad (`CP-HU20-09`) |

### B. Negocio, servicios y recursos (HU-08, 09, 13, 14)

| ID | Tema | Supuesto | Origen | Impacto si cambia | Confirma |
|---|---|---|---|---|---|
| S-05 | HU-08 antelación | Horas enteras, **1 a 720**, por defecto **1**; `GET`/`PUT` en `/businesses/{id}/booking-lead-time` (no en `GET /businesses/{id}`, que es del catálogo) | Supuesto (plan dec. 8) | Cambiar el tope: 1 constante + migración del `CHECK` | PO/Calidad |
| S-06 | HU-09 servicio | Duración **1–1440 min**; precio entero en COP **0–1.000.000.000** (0 = gratuito); nombre ≤ 150, **único por negocio sin distinguir mayúsculas**; descripción opcional ≤ 500 | Supuesto (los topes); el resto del AC | Cambiar topes: DTO + migración | PO |
| S-07 | Quién gestiona | **Solo el proveedor dueño** gestiona su negocio, servicios, recursos, horarios y reservas; **ni un administrador** (recibe 403) | Supuesto (lectura de «solo el dueño») | Añadir `ADMINISTRADOR` a `BusinessAccessService` | Calidad |
| S-08 | HU-13 catálogo | Cualquier rol autenticado lo consulta; ordenado por nombre; tamaño por defecto 20 y **tope 50 que se recorta** (no da 400); solo servicios **activos** | Supuesto | Restringir a CLIENTE: 1 anotación | Calidad |
| S-09 | HU-14 recursos | Tipos fijos **SALA, EQUIPO, PERSONAL** (se acepta cualquier mayúscula); nombre único por negocio; negocio **ajeno en la ruta → 403**, **en el cuerpo → se ignora** (como pide el AC) | Supuesto (plan dec. 7) | Más tipos: enum + `CHECK` | PO |

### C. Asignación, horarios y disponibilidad (HU-18, 19, 20)

| ID | Tema | Supuesto | Origen | Impacto si cambia | Confirma |
|---|---|---|---|---|---|
| S-10 | HU-18 asignación | `PUT` **reemplaza** el conjunto (idempotente y atómico); recurso ajeno o inexistente → **400 con el mismo mensaje** (no revela si es de otro negocio); se permiten recursos **inactivos**; lista vacía válida; máx. 100 | Supuesto | Distinguir 403/404: cambio local | Calidad |
| S-11 | HU-19 horarios | Días ISO (1 = lunes); rangos `[inicio, fin)` (los contiguos no se superponen); último minuto `23:59`; **máx. 10 rangos por día**; `PUT` de la semana reemplaza todo | Supuesto | `24:00`: cambiar el tipo de dato | PO/Calidad |
| S-12 | HU-20 motor | **Paso entre horarios = duración del servicio** (45 min en un rango 09:00–12:00 → 09:00, 09:45, 10:30, 11:15); «sin fecha» = hoy (no un rango); fechas hasta **365 días**; un mismo horario en varios recursos sale una vez con la lista de recursos. Es **pública** y por eso muestra ids y nombres de los recursos (riesgo bajo aceptado) | Supuesto (plan dec. 11 decía 30 min; se cambió a la duración) | Paso fijo de 30 min: cambio local en el motor | PO/Calidad |

### D. Reservas y cancelaciones (HU-22 a 26, 16, 17, 28)

| ID | Tema | Supuesto | Origen | Impacto si cambia | Confirma |
|---|---|---|---|---|---|
| S-13 | HU-22 contrato | El cliente envía **`date`, `startTime`, `endTime`** (el AC tiene el escenario «fin ≤ inicio»); la **duración debe ser la del servicio** (precio fijo por servicio); el inicio puede ser **cualquier minuto libre**, no solo los de la cuadrícula de HU-20 | Supuesto (plan dec. 9) | Duración libre exigiría precio por tiempo | Calidad/PO |
| S-14 | HU-22 anti-overbooking | **Por recurso, no por servicio** (ADR-005); con `resourceId` opcional: sin él se asigna el primer recurso libre por nombre; una carrera en la asignación devuelve 409 y se reintenta | Supuesto (plan dec. 2) | Por servicio: cambiar la clave de la restricción (1 migración) | Calidad |
| S-15 | HU-22 errores | Horario ocupado y fuera del horario del recurso → **409** con mensajes distintos; fecha/horas inválidas, duración, antelación → **400** | Supuesto | Códigos distintos: cambio local | Calidad |
| S-16 | HU-23 | Orden por **fecha de inicio** descendente; existe `GET /users/{id}/bookings` que da **403** para ids ajenos (para poder probar el escenario); solo CLIENTE (ni administrador); filtro opcional por estado | Supuesto | Quitar la ruta extra o abrirla a administración | Calidad |
| S-17 | HU-24 | El proveedor ve **`clientId`, `clientName`, `clientEmail`** (no el celular, porque la reserva no lo guarda) además de fecha, horas y servicio; filtros `from`/`to` inclusivos por **día de Bogotá** | **Decidida** (Simon, 2026-10-10) para los datos del cliente; supuesto para filtros | Quitar correo/id: 2 campos | Calidad (dato personal) |
| S-18 | HU-25 | Se cancela con **≥ 1 hora de antelación** (regla fija, exactamente 1 h sí); motivo **opcional**; origen CLIENTE | Supuesto (la regla de 1 h está en el plan, no en el AC de HU-25) | Parametrizar el plazo | Calidad/PO |
| S-19 | HU-26 | Motivo **obligatorio** (≤ 500); sin regla de 1 h; **no se puede cancelar una reserva que ya empezó**; solo implementa «horario liberado» (el AC dice «liberado o deshabilitado según preferencia») | Supuesto (plan dec. 12) | «Deshabilitar el horario» exigiría bloquear franjas: HU nueva | Calidad |
| S-20 | HU-16 | Confirmación con `confirm: true` en el cuerpo; sin ella y con reservas futuras → **409 `CONFIRMATION_REQUIRED`** con la cantidad (`fields.affectedBookings`) y no cambia nada; cancela **solo las futuras confirmadas del recurso**, con origen `RECURSO_NO_DISPONIBLE`; todo en una transacción | Supuesto | Un flujo de dos pasos con token de confirmación | Calidad |
| S-21 | HU-17 | Reactiva el **mismo recurso** (no duplica); **idempotente**; las reservas canceladas siguen canceladas | Supuesto | — | Calidad |
| S-22 | HU-28 | Solo **el administrador** elimina cuentas (HU-05); la **autoeliminación no existe**; al eliminar, las reservas futuras del usuario (y las de todos sus negocios) pasan a `CANCELADA`/`ELIMINACION_CUENTA`; las pasadas se conservan; los servicios del proveedor se eliminan en cascada | Supuesto (plan dec. 4) | Autoeliminación: endpoint nuevo con contraseña + MFA | PO/Calidad |

### E. Transversales

| ID | Tema | Supuesto | Origen | Impacto si cambia | Confirma |
|---|---|---|---|---|---|
| S-23 | Zona horaria | Todo el negocio opera en **America/Bogota** (sin horario de verano); en la base se guardan instantes UTC | Supuesto | Zona por negocio: columna + conversión | PO |
| S-24 | Límites de intentos | Registro: **5 por IP en 10 min** (el 6.º bloquea 15 min); login/MFA/operaciones sensibles: **5 fallos en 15 min** → `429` 15 min; en memoria. La IP del cliente sale de `X-Forwarded-For` (`server.forward-headers-strategy: native`, 2026-10-10; **por verificar en Render**) | Supuesto (valores) | Variables `RATE_LIMIT_*` | Arquisoft/Calidad |
| S-25 | Sesión | JWT HS256 de **1 h**, sin *refresh token*; revocable por la tabla `sessions` | Decidida (ADR-002) | Refresh token: HU nueva | Arquisoft |
| S-26 | MFA | Obligatorio para ADMINISTRADOR, voluntario para los demás; **anti-replay, cifrado del secreto y reinicio por otro admin pendientes**; ADR-004 **sin aprobar** | Decidida (Simon) / ADR pendiente | Ver ADR-004 | **Juan Esteban y Santiago** |
| S-27 | **Retención de datos personales** | Tras eliminar una cuenta, su **nombre y correo permanecen en las reservas históricas** (visibles al proveedor) para conservar el historial exigido por HU-28 (ADR-006) | Supuesto | Anonimizar a «Cliente eliminado» al borrar: 1 `UPDATE` más en el oyente | **PO/docente** (conviene preguntarlo: es un dato personal) |
| S-28 | Formato de error | Se mantiene `ApiError` y **desde el 2026-10-10 lleva `traceId`** (igual al header `X-Request-Id`; logs JSON en `prod`). **No** se añadió `errorCode/details` como piden los Lineamientos §3.3: `error` y `fields` cumplen ese papel | Supuesto (plan dec. 13); `traceId` por OWASP A09 | Renombrar a `errorCode`/`details`: rompe a quien ya consume el formato | Docente/Calidad |
| S-29 | Paginación | Numeración desde 0; por defecto 20; **tope 50 que se recorta** (no es error); `page < 0` o `size < 1` → 400 | Supuesto | — | Calidad |
| S-30 | Swagger | **Aplicado el 2026-10-10 (ARQ-01)** con springdoc 3.1.1: **apagado por defecto** y encendido con `SWAGGER_ENABLED=true` (perfiles `dev`/`test` lo encienden; en Render solo mientras dure una demostración). La ruta de la documentación es pública cuando está encendida; ver [guía](../api/guia-swagger-openapi.md) | Decidida (Simon: «aplica Swagger primero»); lo de apagado por defecto es supuesto | Dejarlo siempre abierto en Render: quitar el interruptor (OWASP A05: más superficie visible) | Arquisoft |
| S-31 | Identificadores y esquema | UUID como clave primaria, `TIMESTAMPTZ`, dinero en pesos enteros, `ON DELETE SET NULL` hacia `users` (nunca `RESTRICT`), nombres `uk_/ck_/idx_` ([convenciones](../bd/convenciones-bd.md)). El modelo formal de BD sigue en `BIGSERIAL`; **manda Flyway** | Supuesto (`BD-01`) | Alinear el modelo formal | **BD (Andraus)** |
| S-32 | Despliegue | Render (gratis) desde el pipeline con *deploy hook* y auto-deploy apagado; la extensión `btree_gist` funciona allí (verificado). Se duerme a los 15 min y la base gratuita expira ≈ 2026-12-21 | Decidida | Plan de pago / otro proveedor | Arquisoft |

## 3. Deuda técnica y límites conocidos (para decirlo antes de que lo pregunten)

1. **Aprobación de ADR-004 pendiente** y tres piezas del MFA sin hacer (anti-replay, cifrado del secreto, reinicio por otro administrador).
2. **Credenciales demo públicas:** la contraseña del administrador de Render es la publicada en el repositorio (`SEC-01`); la base anterior de Supabase quedó expuesta en el historial de git (`SEC-02`).
3. **Límite de intentos por IP posiblemente global detrás del proxy** (`OWASP-03`): por verificar.
4. **Pequeñas violaciones de ADR-003** heredadas del Sprint 1 (registro de proveedor usa clases internas de `identity`; manejador de errores único que conoce las excepciones de todos): ver ADR-007 §3.
5. **Sin escaneo de dependencias, sin protección de `main` verificada, sin `traceId`/logs JSON** (OWASP A06, A08, A09).
6. **Diagramas de arquitectura del Sprint 1** sin actualizar con `service`, `resource` y `reservation` (`DOC-03`).
7. **Solo una instancia:** los límites de intentos viven en memoria; con más instancias harían falta un almacén compartido.
8. **Notificaciones, autoeliminación de cuenta y las HU de Tier 3** no existen.

## 4. Preguntas prioritarias para Calidad / docente

Las que más cambian lo ya construido, en orden:

1. **S-27** — ¿Deben anonimizarse el nombre y el correo del cliente en las reservas históricas cuando se elimina su cuenta? (dato personal)
2. **S-14** — «No dos reservas para el mismo servicio en el mismo horario»: ¿por **recurso** (como está) o por **servicio**?
3. **S-04 / S-16** — ¿Qué rol puede ver el catálogo (HU-13) y las reservas propias (HU-23)? ¿Puede un administrador?
4. **S-19** — HU-26 dice «horario liberado o deshabilitado según preferencia»: ¿se espera la opción de **deshabilitar**?
5. **S-18** — ¿La regla de **1 hora** para cancelar es de HU-25 o es solo del plan? ¿Se aplica también al proveedor?
6. **S-22** — ¿Entra la **autoeliminación** de cuenta en el sprint?
7. **S-28** — Ya hay `traceId`; ¿se exige además renombrar `error`/`fields` a `errorCode`/`details` (Lineamientos §3.3)?
8. **S-01** — ¿Se evalúa solo lo comprometido (Tier 1 + 2) o también las HU de Tier 3?

## 5. Cómo verificar lo que se afirma aquí

| Afirmación | Cómo comprobarla |
|---|---|
| Los módulos no se importan entidades/repositorios y no hay ciclos | `python docs/arquitectura/verificar_dependencias.py` (devuelve error si aparece una dependencia nueva fuera de [ADR-007 §3](adr/ADR-007-comunicacion-entre-modulos-y-eventos.md)) |
| Los endpoints, su rol y los DTO | [`docs/api/referencia-api-sprint-2.md`](../api/referencia-api-sprint-2.md), generado del código con `python docs/api/generar_referencia.py` |
| El comportamiento de punta a punta | Colección de [Postman](../postman/README.md) (88 peticiones, 0 fallos contra Render el 2026-10-10) y 614 pruebas automáticas |
| La seguridad por riesgo | [OWASP Top 10](../seguridad/owasp-top10-sprint-2.md) |
