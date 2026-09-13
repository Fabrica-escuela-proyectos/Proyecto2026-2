# Manejo de errores de API — Sprint 1

## 1. Introducción

La API utilizará un formato común para las respuestas de error, con el objetivo de mantener un comportamiento consistente entre los diferentes endpoints del sistema.

Los errores serán procesados desde el backend y se devolverán utilizando códigos de estado HTTP adecuados y un cuerpo de respuesta estructurado.

El sistema no deberá revelar información sensible o detalles internos de la aplicación en los mensajes enviados al cliente.

---

## 2. Formato general de error

Las respuestas de error utilizarán la siguiente estructura:

```json
{
  "timestamp": "2026-09-11T20:30:00",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Los datos enviados no son válidos",
  "path": "/api/v1/users"
}
```

### Campos

| Campo       | Tipo    | Descripción                          |
| ----------- | ------- | ------------------------------------ |
| `timestamp` | String  | Fecha y hora en que ocurrió el error |
| `status`    | Integer | Código de estado HTTP                |
| `error`     | String  | Tipo de error                        |
| `message`   | String  | Mensaje general para el cliente      |
| `path`      | String  | Endpoint donde ocurrió el error      |

En errores de validación se podrá incluir información adicional sobre los campos que presentan problemas.

---

## 3. Códigos HTTP principales

Se utilizarán principalmente los siguientes códigos:

| Código | Tipo                  | Uso                                                             |
| ------ | --------------------- | --------------------------------------------------------------- |
| `400`  | Bad Request           | Datos inválidos o solicitud incorrecta                          |
| `401`  | Unauthorized          | Usuario no autenticado o credenciales inválidas                 |
| `403`  | Forbidden             | Usuario autenticado pero sin permisos suficientes               |
| `404`  | Not Found             | Recurso solicitado inexistente                                  |
| `409`  | Conflict              | Conflicto con el estado actual, por ejemplo email ya registrado |
| `429`  | Too Many Requests     | Exceso de solicitudes en un período determinado                 |
| `500`  | Internal Server Error | Error interno no controlado                                     |

---

## 4. Errores de validación — 400

Se utilizará `400 Bad Request` cuando los datos enviados no cumplan las validaciones requeridas.

Por ejemplo, si un usuario intenta registrarse sin correo electrónico:

```json
{
  "timestamp": "2026-09-11T20:30:00",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Los datos enviados no son válidos",
  "path": "/api/v1/users",
  "fields": {
    "email": "El correo electrónico es obligatorio"
  }
}
```

Las validaciones serán realizadas en el backend y no dependerán únicamente de las validaciones realizadas en el cliente.

---

## 5. Errores de autenticación — 401

Se utilizará `401 Unauthorized` cuando el usuario no pueda autenticarse correctamente o cuando no exista una autenticación válida.

Por ejemplo:

```json
{
  "timestamp": "2026-09-11T20:30:00",
  "status": 401,
  "error": "UNAUTHORIZED",
  "message": "Las credenciales no son válidas",
  "path": "/api/v1/auth/login"
}
```

El mensaje no deberá indicar si el correo existe o si específicamente la contraseña fue incorrecta, con el fin de evitar revelar información sobre las cuentas registradas.

Tampoco se incluirán contraseñas, tokens completos u otra información sensible en la respuesta.

---

## 6. Errores de autorización — 403

Se utilizará `403 Forbidden` cuando el usuario esté autenticado pero no tenga permisos suficientes para realizar una operación.

Ejemplo:

```json
{
  "timestamp": "2026-09-11T20:30:00",
  "status": 403,
  "error": "FORBIDDEN",
  "message": "No tiene permisos para realizar esta operación",
  "path": "/api/v1/users/15/role"
}
```

Este código será utilizado especialmente en las reglas de HU05 y HU06.

Por ejemplo:

* Un Cliente intenta modificar el rol de un usuario.
* Un Proveedor intenta acceder a información de otro negocio.
* Un usuario intenta modificar sus propios permisos.
* Un usuario intenta realizar una operación reservada para Administradores.

---

## 7. Recurso inexistente — 404

Se utilizará `404 Not Found` cuando el recurso solicitado no exista.

Ejemplo:

```json
{
  "timestamp": "2026-09-11T20:30:00",
  "status": 404,
  "error": "NOT_FOUND",
  "message": "El recurso solicitado no existe",
  "path": "/api/v1/users/999"
}
```

No se deberán revelar detalles internos de la base de datos o de la implementación.

---

## 8. Conflictos — 409

Se utilizará `409 Conflict` cuando la solicitud sea válida pero entre en conflicto con información existente.

Un caso principal durante el Sprint 1 es el registro de usuarios con un email o celular que ya se encuentra registrado.

Ejemplo:

```json
{
  "timestamp": "2026-09-11T20:30:00",
  "status": 409,
  "error": "CONFLICT",
  "message": "No es posible completar el registro con los datos proporcionados",
  "path": "/api/v1/users"
}
```

El backend deberá validar esta condición antes de crear el usuario y la base de datos también deberá mantener las restricciones de unicidad correspondientes.

---

## 9. Límite de solicitudes — 429

Se utilizará `429 Too Many Requests` cuando un usuario u origen supere el límite de solicitudes permitido.

Este comportamiento es especialmente relevante para HU01, debido al requisito de bloquear temporalmente múltiples intentos de registro provenientes del mismo origen en un período corto.

Ejemplo:

```json
{
  "timestamp": "2026-09-11T20:30:00",
  "status": 429,
  "error": "TOO_MANY_REQUESTS",
  "message": "Se han realizado demasiadas solicitudes. Intente nuevamente más tarde",
  "path": "/api/v1/users"
}
```

El mecanismo específico para controlar estos límites será definido durante la implementación.

---

## 10. Error interno — 500

Se utilizará `500 Internal Server Error` cuando ocurra un error interno que no pueda ser tratado mediante una respuesta específica.

Ejemplo:

```json
{
  "timestamp": "2026-09-11T20:30:00",
  "status": 500,
  "error": "INTERNAL_SERVER_ERROR",
  "message": "Ocurrió un error interno al procesar la solicitud",
  "path": "/api/v1/users"
}
```

No se deberán enviar al cliente:

* Stack traces.
* Consultas SQL.
* Nombres de tablas internas.
* Contraseñas.
* Tokens.
* Variables de configuración.
* Credenciales.
* Información sensible de otros usuarios.

Los detalles técnicos necesarios para diagnosticar el problema deberán permanecer en los logs internos, aplicando las mismas restricciones de seguridad.

---

## 11. Manejo centralizado

Los errores serán manejados de forma centralizada en el backend mediante un mecanismo común para las excepciones de la aplicación.

En Spring Boot se podrá implementar utilizando un manejador global de excepciones, por ejemplo mediante `@RestControllerAdvice`.

Conceptualmente:

```text
Solicitud HTTP
      ↓
Controller
      ↓
Application / Service
      ↓
Excepción
      ↓
Manejador global
      ↓
Respuesta de error estandarizada
```

Esto evita implementar manualmente el mismo tratamiento de errores en cada controlador.

---

## 12. Relación con las historias de usuario

| HU   | Errores principales        |
| ---- | -------------------------- |
| HU01 | `400`, `409`, `429`        |
| HU02 | `400`, `401`               |
| HU03 | `400`, `409`, `429`        |
| HU04 | `401`, `500`               |
| HU05 | `400`, `401`, `403`, `404` |
| HU06 | `401`, `403`, `404`        |

Los códigos podrán ampliarse posteriormente cuando se incorporen las funcionalidades de servicios, recursos, reservas y reportes.

---

## 13. Principios de seguridad

El manejo de errores seguirá los siguientes principios:

* No revelar información sensible.
* No indicar si una cuenta específica existe cuando esto pueda facilitar ataques.
* No devolver contraseñas ni tokens.
* No exponer detalles internos de la aplicación.
* Utilizar códigos HTTP apropiados.
* Mantener un formato uniforme de respuesta.
* Registrar internamente los errores relevantes sin almacenar información sensible.
* Separar la información mostrada al usuario de los detalles técnicos utilizados para diagnóstico.
