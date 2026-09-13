# DTOs iniciales — Sprint 1

## 1. Introducción

Los DTOs (Data Transfer Objects) permiten definir los datos que serán enviados y recibidos mediante los endpoints de la API. Su utilización evita exponer directamente las entidades internas de la aplicación o las estructuras utilizadas para la persistencia en la base de datos.

Para el Sprint 1 se definen los DTOs necesarios para las historias de usuario relacionadas con identidad, autenticación y gestión de roles.

---

## 2. HU01 — Registrar cliente

### RegisterUserRequest

Este DTO representa los datos necesarios para registrar un nuevo cliente.

| Campo       | Tipo   | Obligatorio | Descripción                 |
| ----------- | ------ | ----------- | --------------------------- |
| `fullName`  | String | Sí          | Nombre completo del usuario |
| `email`     | String | Sí          | Correo electrónico          |
| `cellphone` | String | Sí          | Número de celular           |
| `password`  | String | Sí          | Contraseña del usuario      |

Ejemplo:

```json
{
  "fullName": "Juan Pérez",
  "email": "juan@example.com",
  "cellphone": "3001234567",
  "password": "Password*123"
}
```

El DTO no incluye el campo `role`. El rol será asignado por el servidor como `CLIENTE`.

La contraseña será utilizada únicamente durante el proceso de autenticación y registro. No será almacenada directamente en la base de datos ni incluida en registros de auditoría.

---

## 3. HU02 — Inicio de sesión

### LoginRequest

Este DTO contiene las credenciales necesarias para iniciar sesión.

| Campo      | Tipo   | Obligatorio | Descripción                   |
| ---------- | ------ | ----------- | ----------------------------- |
| `email`    | String | Sí          | Correo electrónico registrado |
| `password` | String | Sí          | Contraseña del usuario        |

Ejemplo:

```json
{
  "email": "juan@example.com",
  "password": "Password*123"
}
```

### LoginResponse

Este DTO representa la información retornada después de una autenticación exitosa.

| Campo       | Tipo   | Descripción                                |
| ----------- | ------ | ------------------------------------------ |
| `token`     | String | Token de autenticación                     |
| `expiresIn` | Long   | Tiempo de expiración del token en segundos |
| `role`      | String | Rol principal del usuario                  |

Ejemplo:

```json
{
  "token": "JWT",
  "expiresIn": 3600,
  "role": "CLIENTE"
}
```

El token mostrado es únicamente representativo. La implementación definitiva establecerá el mecanismo de autenticación utilizado por el backend.

---

## 4. HU03 — Registrar proveedor

### RegisterProviderRequest

Este DTO representa los datos necesarios para registrar un proveedor y la información básica de su negocio.

| Campo          | Tipo   | Obligatorio | Descripción                   |
| -------------- | ------ | ----------- | ----------------------------- |
| `fullName`     | String | Sí          | Nombre completo del proveedor |
| `email`        | String | Sí          | Correo electrónico            |
| `cellphone`    | String | Sí          | Número de celular             |
| `password`     | String | Sí          | Contraseña                    |
| `businessName` | String | Sí          | Nombre del negocio            |

Ejemplo:

```json
{
  "fullName": "Carlos Gómez",
  "email": "carlos@example.com",
  "cellphone": "3019876543",
  "password": "Password*123",
  "businessName": "Centro Deportivo ABC"
}
```

El DTO no permite que el usuario defina el rol. El backend asignará automáticamente el rol `PROVEEDOR`.

---

## 5. HU04 — Cerrar sesión

Para el cierre de sesión no se requiere un DTO de entrada específico.

El endpoint:

```text
POST /api/v1/auth/logout
```

utilizará la información de autenticación asociada a la solicitud para identificar y revocar la sesión correspondiente.

No se enviará el token como parte del cuerpo de la solicitud cuando el mecanismo de autenticación lo maneje mediante el encabezado correspondiente.

---

## 6. HU05 — Gestionar roles y permisos

### ChangeUserRoleRequest

Este DTO representa el nuevo rol que un administrador solicita asignar a un usuario.

| Campo  | Tipo   | Obligatorio | Descripción              |
| ------ | ------ | ----------- | ------------------------ |
| `role` | String | Sí          | Rol que se desea asignar |

Ejemplo:

```json
{
  "role": "ADMINISTRADOR"
}
```

El backend deberá validar que el rol exista y que la operación esté permitida de acuerdo con las reglas de negocio.

No se permitirá que un usuario modifique su propio rol o permisos.

Tampoco se permitirá modificar el rol de un usuario que tenga el rol `PROVEEDOR`, de acuerdo con las reglas definidas para la HU05.

---

## 7. HU06 — Control de acceso

HU06 no requiere un DTO específico.

El control de acceso se realizará mediante la información de autenticación del usuario y las reglas de autorización definidas para cada endpoint.

La aplicación deberá comprobar:

* Que exista una sesión válida.
* Que la sesión no haya expirado o sido revocada.
* Que el usuario tenga el rol correspondiente.
* Que el usuario tenga permiso para realizar la operación.
* Que el usuario tenga acceso al recurso específico solicitado.

Por ejemplo, un proveedor podrá acceder a los recursos pertenecientes a su propio negocio, pero no a los recursos de otro proveedor.

---

## 8. Validaciones de los DTOs

Las validaciones de entrada se realizarán antes de ejecutar la lógica de negocio.

Entre las validaciones iniciales se encuentran:

* Campos obligatorios.
* Formato válido del correo electrónico.
* Formato válido del número de celular.
* Longitud mínima de la contraseña.
* Existencia de mayúsculas y minúsculas en la contraseña.
* Existencia de un carácter especial en la contraseña.
* Valores válidos para los roles.
* Restricción de campos no permitidos.

Las validaciones se realizarán en el backend y no se confiará únicamente en las validaciones realizadas por el cliente.

---

## 9. Relación entre DTOs y endpoints

| Endpoint                            | HU   | DTO de entrada            | DTO de salida          |
| ----------------------------------- | ---- | ------------------------- | ---------------------- |
| `POST /api/v1/users`                | HU01 | `RegisterUserRequest`     | Respuesta de registro  |
| `POST /api/v1/auth/login`           | HU02 | `LoginRequest`            | `LoginResponse`        |
| `POST /api/v1/providers`            | HU03 | `RegisterProviderRequest` | Respuesta de registro  |
| `POST /api/v1/auth/logout`          | HU04 | No requiere               | Respuesta de operación |
| `PATCH /api/v1/users/{userId}/role` | HU05 | `ChangeUserRoleRequest`   | Respuesta de operación |
| Endpoints protegidos                | HU06 | Según operación           | Según operación        |

---

## 10. Ubicación propuesta en el backend

Los DTOs relacionados con identidad y acceso se organizarán dentro del módulo correspondiente:

```text
backend/src/main/java/com/codefactory/reservas/
└── identity/
    └── controller/
        └── dto/
            ├── RegisterUserRequest.java
            ├── LoginRequest.java
            ├── LoginResponse.java
            └── ChangeUserRoleRequest.java
```

El DTO de registro de proveedor podrá ubicarse inicialmente en el módulo `provider`:

```text
backend/src/main/java/com/codefactory/reservas/
└── provider/
    └── controller/
        └── dto/
            └── RegisterProviderRequest.java
```

Esta organización mantiene los DTOs asociados al módulo que posee la responsabilidad de la funcionalidad correspondiente.

---

## 11. Consideraciones de seguridad

Los DTOs no deberán contener información que no sea necesaria para la operación solicitada.

En particular:

* No se permitirá enviar el rol durante el registro de clientes.
* No se permitirá enviar un rol privilegiado durante el registro de proveedores.
* Las contraseñas no se almacenarán en texto plano.
* Las contraseñas no se incluirán en logs.
* Los tokens completos no deberán registrarse en auditoría.
* Los campos recibidos deberán validarse antes de procesarlos.
* Los datos internos de las entidades de persistencia no serán expuestos directamente mediante los DTOs.
