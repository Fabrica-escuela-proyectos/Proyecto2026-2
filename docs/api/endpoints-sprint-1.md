# Endpoints iniciales — Sprint 1

## 1. Introducción

Para el Sprint 1 se propone una API REST versionada mediante `/api/v1`. Los endpoints iniciales están enfocados principalmente en el módulo de **Identity & Access**, debido a que las seis historias de usuario priorizadas para este sprint corresponden a registro, autenticación, cierre de sesión, gestión de roles y control de acceso.

Los endpoints definidos en esta etapa representan el diseño inicial de la API y posteriormente serán documentados mediante OpenAPI/Swagger.

---

## 2. Registro de cliente — HU01

**Método:** `POST`

**Endpoint:**

```text
/api/v1/users
```

**Propósito:**
Registrar un nuevo usuario con rol Cliente.

**Datos de entrada:**

```json
{
  "fullName": "Juan Pérez",
  "email": "juan@example.com",
  "cellphone": "3001234567",
  "password": "Password*123"
}
```

**Procesamiento principal:**

1. Validar los campos recibidos.
2. Verificar que el email no esté registrado.
3. Verificar que el celular no esté registrado.
4. Validar la política de contraseña.
5. Crear el usuario.
6. Almacenar la contraseña mediante un mecanismo de hash seguro.
7. Asignar automáticamente el rol `CLIENTE`.
8. Registrar el evento correspondiente en auditoría.
9. Retornar una respuesta indicando que el registro fue exitoso.

El cliente no podrá definir directamente el rol de su cuenta. El rol será asignado por el servidor.

---

## 3. Inicio de sesión — HU02

**Método:** `POST`

**Endpoint:**

```text
/api/v1/auth/login
```

**Propósito:**
Autenticar un usuario y generar una sesión válida.

**Datos de entrada:**

```json
{
  "email": "juan@example.com",
  "password": "Password*123"
}
```

**Procesamiento principal:**

1. Buscar el usuario mediante su email.
2. Verificar la contraseña.
3. Verificar que la cuenta pueda iniciar sesión.
4. Obtener el rol del usuario.
5. Crear una sesión con una duración máxima de una hora.
6. Generar el token de autenticación.
7. Registrar el evento de auditoría.
8. Si el usuario requiere MFA, realizar el paso adicional de verificación.

**Respuesta propuesta:**

```json
{
  "token": "JWT",
  "expiresIn": 3600,
  "role": "CLIENTE"
}
```

El token mostrado es únicamente representativo. Las contraseñas no serán almacenadas en texto plano ni se registrará el token completo en los logs de auditoría.

---

## 4. Registro de proveedor — HU03

**Método:** `POST`

**Endpoint:**

```text
/api/v1/providers
```

**Propósito:**
Registrar un proveedor de servicios junto con la información básica de su negocio.

**Datos de entrada:**

```json
{
  "fullName": "Carlos Gómez",
  "email": "carlos@example.com",
  "cellphone": "3019876543",
  "password": "Password*123",
  "businessName": "Centro Deportivo ABC"
}
```

**Procesamiento principal:**

1. Validar los campos recibidos.
2. Verificar que el email no esté registrado.
3. Validar la política de contraseña.
4. Crear el usuario.
5. Asignar automáticamente el rol `PROVEEDOR`.
6. Crear la información correspondiente al proveedor.
7. Crear o asociar la información básica del negocio.
8. Registrar el evento correspondiente en auditoría.

El rol será asignado por el servidor. Si el cliente intenta enviar un rol diferente, este será ignorado y no podrá utilizarse para obtener privilegios adicionales.

---

## 5. Cerrar sesión — HU04

**Método:** `POST`

**Endpoint:**

```text
/api/v1/auth/logout
```

**Propósito:**
Cerrar la sesión del usuario e invalidar la sesión activa.

**Autenticación:** requerida.

**Procesamiento principal:**

1. Identificar la sesión utilizada.
2. Revocar la sesión correspondiente.
3. Impedir que la sesión revocada pueda utilizarse nuevamente para acceder a operaciones protegidas.
4. Registrar el evento de auditoría.

La sesión tendrá una duración máxima de una hora. Una vez finalizado este tiempo, las operaciones protegidas deberán requerir una nueva autenticación.

---

## 6. Gestionar roles y permisos — HU05

**Método:** `PATCH`

**Endpoint:**

```text
/api/v1/users/{userId}/role
```

**Propósito:**
Permitir a un administrador autorizado modificar el rol de un usuario cuando la regla de negocio lo permita.

**Autenticación:** requerida.

**Rol requerido:** `ADMINISTRADOR`.

**Datos de entrada:**

```json
{
  "role": "ADMINISTRADOR"
}
```

**Procesamiento principal:**

1. Verificar que el usuario que realiza la solicitud sea administrador.
2. Verificar que el usuario objetivo exista.
3. Validar que el rol solicitado exista.
4. Impedir que un usuario modifique sus propios permisos o rol.
5. Impedir modificaciones del rol de un `PROVEEDOR`.
6. Si el nuevo rol es `ADMINISTRADOR`, iniciar el proceso requerido para la configuración de MFA.
7. Registrar el cambio realizado en auditoría.

Las solicitudes que intenten realizar cambios no permitidos deberán ser rechazadas con una respuesta de error uniforme.

---

## 7. Control de acceso — HU06

HU06 no requiere un endpoint independiente. El control de acceso se aplicará sobre los endpoints protegidos de la plataforma.

Por ejemplo:

```text
GET /api/v1/users/{userId}
```

Antes de permitir una operación protegida, el backend deberá comprobar:

```text
¿Existe una sesión válida?
        ↓
¿La sesión está vigente?
        ↓
¿Qué rol tiene el usuario?
        ↓
¿Tiene permiso para realizar la operación?
        ↓
¿Puede acceder a este recurso?
        ↓
Permitir / Denegar
```

El control de acceso deberá considerar tanto el rol como la pertenencia del recurso al usuario o negocio correspondiente.

Por ejemplo, un proveedor podrá administrar los recursos pertenecientes a su propio negocio, pero no podrá acceder a los recursos pertenecientes a otro proveedor.

---

## 8. Resumen de endpoints

| HU   | Método  | Endpoint                         | Autenticación | Rol principal       |
| ---- | ------- | -------------------------------- | ------------- | ------------------- |
| HU01 | `POST`  | `/api/v1/users`                  | No            | Público             |
| HU02 | `POST`  | `/api/v1/auth/login`             | No            | Público             |
| HU03 | `POST`  | `/api/v1/providers`              | No            | Público             |
| HU04 | `POST`  | `/api/v1/auth/logout`            | Sí            | Usuario autenticado |
| HU05 | `PATCH` | `/api/v1/users/{userId}/role`    | Sí            | Administrador       |
| HU06 | —       | Se aplica a endpoints protegidos | Sí            | Según la operación  |

---

## 9. Alcance de los endpoints en el Sprint 1

Los endpoints definidos anteriormente corresponden a las funcionalidades de identidad y acceso priorizadas para el Sprint 1.

No se definen todavía los endpoints principales de servicios, recursos, reservas y reportes, ya que sus funcionalidades serán desarrolladas principalmente en los siguientes sprints.

Sin embargo, los módulos correspondientes ya forman parte de la arquitectura propuesta, permitiendo incorporar sus endpoints posteriormente sin modificar la estructura principal del backend.

Los endpoints definidos en este documento servirán como base para la posterior implementación de los DTOs, servicios, controladores y contratos OpenAPI/Swagger.
