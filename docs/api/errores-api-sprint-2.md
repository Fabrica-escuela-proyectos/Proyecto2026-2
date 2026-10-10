# Errores y cambios de contrato de la API — Sprint 2

Complementa `errores-api-sprint-1.md`: el formato de la respuesta de error no cambia (`timestamp`, `status`, `error`, `message`, `path` y, solo en validación, `fields`). Aquí se documentan los códigos `error` nuevos y los endpoints existentes cuyo comportamiento cambió con la política de MFA (ADR-004) y la corrección de los issues de Calidad #8–#11.

## 1. Códigos de error nuevos

| `error` | HTTP | Cuándo | Qué debe hacer el cliente |
|---|---|---|---|
| `MFA_REQUIRED` | 401 | Login de un administrador con MFA activa cuya contraseña es válida pero no envió `mfaCode`; o una operación sensible sin el header `X-MFA-Code` | Pedir el código de 6 dígitos y repetir la petición |
| `MFA_ENROLLMENT_REQUIRED` | 403 | Un administrador sin MFA activa usó una ruta distinta de `/api/v1/auth/mfa/**` y `/api/v1/auth/logout` | Llevar al usuario a configurar MFA: `POST /auth/mfa/setup` y `POST /auth/mfa/activate` |
| `METHOD_NOT_ALLOWED` | 405 | Método HTTP no soportado por la ruta (incluye cabecera `Allow`) | Corregir el método |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | `Content-Type` distinto de `application/json` en un endpoint que lo exige | Enviar `application/json` |
| `NOT_ACCEPTABLE` | 406 | El cliente no acepta `application/json` | Aceptar `application/json` |

Ejemplo (`401 MFA_REQUIRED`):

```json
{
  "timestamp": "2026-10-07T20:40:12.512",
  "status": 401,
  "error": "MFA_REQUIRED",
  "message": "Se requiere el código de verificación (MFA) para iniciar sesión",
  "path": "/api/v1/auth/login"
}
```

## 2. Códigos que ahora se devuelven donde antes salía `500`

| Situación | Antes | Ahora |
|---|---|---|
| Cuerpo vacío o JSON mal formado en cualquier endpoint (issue #11) | `500 INTERNAL_SERVER_ERROR` | `400 VALIDATION_ERROR` — "El cuerpo de la solicitud es inválido o está vacío" |
| UUID o número inválido en la ruta o en un parámetro, p. ej. `GET /users/abc` (issue #11) | `500` | `400 VALIDATION_ERROR` — "El parámetro 'userId' tiene un formato inválido" |
| Falta un parámetro o cabecera obligatoria (issue #11) | `500` | `400 VALIDATION_ERROR` |
| Ruta inexistente, con sesión válida (issue #11) | `500` | `404 NOT_FOUND` (sin sesión sigue siendo `401`: no se revela qué rutas existen) |
| Dos registros simultáneos con el mismo correo o celular (issue #10) | `500` para el que perdía la carrera | `409 CONFLICT` — "El correo electrónico ya está en uso" / "El número de celular ya está en uso" |
| Valor demasiado largo que llega a la base de datos | `500` | `400 VALIDATION_ERROR` — "Alguno de los valores enviados supera la longitud permitida" |

## 3. Cambios en endpoints existentes

| Endpoint | Cambio |
|---|---|
| `POST /api/v1/auth/login` | Un administrador con MFA activa debe enviar `mfaCode` (sin él: `401 MFA_REQUIRED`; incorrecto: `401` genérico). Tras 5 fallos seguidos por IP + correo: `429` durante 15 minutos (incluso con credenciales correctas). El campo `email` admite hasta 150 caracteres, `password` hasta 72 y `mfaCode` hasta 10. |
| `PATCH /api/v1/users/{id}/role` · `DELETE /api/v1/users/{id}` | Exigen el header **`X-MFA-Code`** con el código vigente del administrador (sin él: `401 MFA_REQUIRED`; incorrecto: `401`; 5 fallos seguidos: `429`). Un Cliente o Proveedor sigue recibiendo `403` antes de que se pida el código. |
| `POST /api/v1/users` · `POST /api/v1/providers` | Límites de longitud (issue #9): nombre, correo y nombre del negocio ≤ 150 caracteres; contraseña ≤ 72 caracteres (y ≤ 72 bytes en UTF-8). Más de 5 intentos de registro por IP en 10 minutos: el 6.º y los siguientes responden `429` durante 15 minutos (issue #8). |
| Todas las rutas protegidas | Un administrador sin MFA activa recibe `403 MFA_ENROLLMENT_REQUIRED` en todo salvo `/api/v1/auth/mfa/**`, `/api/v1/auth/logout` y `/actuator/health`. |

## 4. Configuración

Los límites de intentos se ajustan por entorno (valores por defecto entre paréntesis):

| Variable | Qué controla |
|---|---|
| `RATE_LIMIT_REGISTRATION_MAX_ATTEMPTS` (5) | Intentos de registro permitidos por IP antes de bloquear |
| `RATE_LIMIT_AUTH_MAX_ATTEMPTS` (5) | Fallos de login / código MFA / confirmación antes de bloquear |

La ventana y la duración del bloqueo están en `application.yml` (`security.rate-limit.*`). Para una corrida de pruebas del equipo de Calidad que registre muchas cuentas desde una sola IP conviene subir `RATE_LIMIT_REGISTRATION_MAX_ATTEMPTS` en ese entorno; **no** en producción.

## 5. Códigos de error añadidos por las HU de reservas

| `error` | HTTP | Cuándo | Campos extra |
|---|---|---|---|
| `CONFIRMATION_REQUIRED` | 409 | HU-16: desactivar un recurso con reservas futuras sin enviar `confirm: true`. **No se cambió nada.** | `fields.affectedBookings`: cantidad de reservas que se cancelarían (texto) |

Ejemplo:

```json
{
  "timestamp": "2026-10-10T14:32:10.118",
  "status": 409,
  "error": "CONFIRMATION_REQUIRED",
  "message": "El recurso tiene 2 reserva(s) futura(s) confirmada(s) que se cancelarán si continúa. Confirme la desactivación para proceder",
  "path": "/api/v1/resources/6b0d…/deactivation",
  "fields": { "affectedBookings": "2" }
}
```

Los demás errores nuevos reutilizan los códigos ya existentes (`VALIDATION_ERROR`, `NOT_FOUND`, `FORBIDDEN`, `CONFLICT`); lo que cambia es el `message`.

## 6. Mapa excepción → respuesta HTTP

Todo el mapeo vive en `GlobalExceptionHandler` (una sola clase): los controladores no construyen respuestas de error. **Regla de diseño:** un mismo mensaje para «no existe» y «no es tuyo» cuando revelar la diferencia filtraría información (p. ej. recursos ajenos en HU-18: `400` con un único texto), y `403` genérico cuando el recurso existe pero es de otro usuario.

| HTTP | `error` | Excepciones (de dominio) y situación |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` (Bean Validation, con `fields`); cuerpo vacío o JSON roto; id/parámetro con formato inválido; `InvalidPaginationException`; `InvalidBookingException` (fecha, rango de horas, duración distinta a la del servicio, antelación, recurso no asignado, estado inválido); `InvalidAvailabilityException` (horario del recurso: formato, inicio ≥ fin, rangos superpuestos, día repetido); `InvalidAvailabilityQueryException` (fecha de la consulta de HU-20 inválida, pasada o a más de 365 días); `InvalidResourceAssignmentException` (HU-18); `RoleNotFoundException`, `InvalidMfaCodeException`, `MfaNotConfiguredException` |
| 401 | `UNAUTHORIZED` · `MFA_REQUIRED` | Sin sesión o token inválido/expirado/revocado; credenciales inválidas (mensaje genérico); falta el código MFA |
| 403 | `FORBIDDEN` · `MFA_ENROLLMENT_REQUIRED` | `AccessDeniedException` (rol insuficiente o recurso/negocio/reserva de otro usuario, incluida la ruta `/users/{otro}/bookings`); `SelfModificationException`, `ProviderRoleImmutableException`, `AdminDeletionNotAllowedException` (HU-05); administrador sin MFA activa |
| 404 | `NOT_FOUND` | `UserNotFoundException`, `ProviderNotFoundException`, `BusinessNotFoundException`, `ServiceNotFoundException`, `ResourceNotFoundException`, `BookingNotFoundException`; `ServiceNotAvailableException` (servicio inexistente, inactivo o de un proveedor inactivo: un solo mensaje «El servicio no está disponible»); ruta inexistente con sesión |
| 405 · 406 · 415 | `METHOD_NOT_ALLOWED` · `NOT_ACCEPTABLE` · `UNSUPPORTED_MEDIA_TYPE` | Método, `Accept` o `Content-Type` no soportado |
| 409 | `CONFLICT` · `CONFIRMATION_REQUIRED` | Correo o celular duplicado; `DuplicateServiceNameException`, `DuplicateResourceNameException` (nombre repetido en el negocio, sin distinguir mayúsculas); `SlotNotAvailableException` (horario ocupado o fuera del horario del recurso); `BookingNotCancellableException` (ya cancelada, completada, ya iniciada o fuera del plazo de 1 hora); violación de unicidad en la base (`23505`) y de la restricción anti-overbooking (`23P01`); confirmación pendiente (HU-16) |
| 429 | `TOO_MANY_REQUESTS` | `TooManyRequestsException`: límite de registros (5 por IP en 10 min) o de fallos de login/MFA (5 en 15 min) |
| 500 | `INTERNAL_SERVER_ERROR` | Cualquier otra excepción: mensaje genérico al cliente, detalle completo solo en el log del servidor |

## 7. Cuál es el mensaje esperado por HU (para escribir casos de prueba)

| HU | Situación | HTTP | Mensaje (o `fields`) |
|---|---|---|---|
| HU-09 | Nombre de servicio repetido | 409 | «Ya existe un servicio con ese nombre en el negocio» |
| HU-14 | Nombre de recurso repetido / tipo fuera de la lista | 409 / 400 | «Ya existe un recurso con ese nombre en el negocio» / `fields.type`: «El tipo debe ser SALA, EQUIPO o PERSONAL» |
| HU-18 | Recurso inexistente o de otro negocio | 400 | «Uno o más recursos no existen o no pertenecen al negocio del servicio» |
| HU-19 | Rango inválido / superpuesto | 400 | «El rango horario no es válido: la hora de inicio debe ser anterior a la de fin» / «Los rangos horarios de un mismo día no pueden superponerse» |
| HU-20 | Fecha inválida / pasada / servicio no disponible | 400 / 400 / 404 | «La fecha no es válida: use el formato yyyy-MM-dd» / «Debe seleccionar una fecha futura o la fecha actual» / «El servicio no está disponible» |
| HU-22 | Horario ocupado / fuera de horario | 409 | «El horario seleccionado no está disponible: ya tiene una reserva» / «…queda fuera del horario de atención» |
| HU-22 | Fin ≤ inicio / sin fecha | 400 | «El rango de horas es inválido: la hora de fin debe ser posterior a la de inicio» / `fields.date`: «La fecha es obligatoria» |
| HU-25 | Ya cancelada / fuera de plazo | 409 | «La reserva ya está cancelada» / «Solo se puede cancelar una reserva con al menos 1 hora de antelación a su inicio» |
| HU-26 | Sin motivo / reserva ya iniciada | 400 / 409 | `fields.reason`: «El motivo de la cancelación es obligatorio» / «La reserva ya inició o finalizó y no se puede cancelar» |
