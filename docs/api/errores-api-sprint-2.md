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
