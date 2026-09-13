# ADR-002 — Estrategia de autenticación y sesiones

* **Estado:** Aceptado
* **Decisión:** Utilizar Spring Security junto con autenticación basada en JWT y control de sesiones con una duración máxima de una hora.

## 1. Contexto

La plataforma requiere autenticar clientes, proveedores y administradores, además de controlar el acceso a las funcionalidades según el rol y los permisos del usuario.

Las historias de usuario del Sprint 1 establecen requisitos relacionados con autenticación, cierre de sesión, expiración de sesiones, revocación de acceso, protección de información sensible y verificación adicional para cuentas administrativas.

Por lo tanto, se necesita un mecanismo que permita identificar al usuario en las solicitudes protegidas y controlar el tiempo durante el cual una autenticación permanece válida.

## 2. Decisión

Se utilizará **Spring Security** como mecanismo principal de seguridad del backend.

La autenticación utilizará **JSON Web Tokens (JWT)** para identificar al usuario en las solicitudes posteriores al inicio de sesión.

Cada token tendrá una duración máxima de **una hora**, de acuerdo con los requisitos definidos para las historias de usuario del Sprint 1.

Adicionalmente, se utilizará una estructura de sesiones en la base de datos para permitir el control de las sesiones y su revocación.

La aplicación no almacenará el JWT completo como mecanismo principal de persistencia de sesión. La información almacenada en la tabla de sesiones será únicamente la necesaria para identificar y controlar la sesión.

## 3. Flujo de autenticación

El flujo general será:

```text
Usuario
   │
   ▼
POST /api/v1/auth/login
   │
   ▼
Identity & Access
   │
   ├── Buscar usuario
   ├── Verificar contraseña
   ├── Obtener rol
   └── Crear sesión
   │
   ▼
Generar JWT
   │
   ▼
Respuesta de autenticación
```

Posteriormente, las solicitudes protegidas utilizarán el token para identificar al usuario.

```text
Solicitud protegida
        │
        ▼
JWT
        │
        ▼
Spring Security
        │
        ▼
Usuario autenticado
        │
        ▼
Autorización
        │
        ▼
Permitir / Denegar
```

## 4. Expiración de sesión

La sesión tendrá una duración máxima de una hora.

Cuando el token o la sesión hayan superado este tiempo, las operaciones protegidas deberán ser rechazadas y el usuario deberá autenticarse nuevamente.

Esto permite cumplir con el requisito de expiración definido en HU02 y HU04.

## 5. Revocación de sesiones

Aunque el JWT tenga una fecha de expiración, el sistema deberá permitir revocar una sesión antes de que llegue a su vencimiento.

Para esto se utilizará una tabla de sesiones que permita mantener información de control como:

* Identificador de la sesión.
* Usuario asociado.
* Fecha de creación.
* Fecha de expiración.
* Estado de la sesión.
* Información necesaria para identificar o revocar la sesión.

No se almacenará la contraseña ni información sensible innecesaria.

El mecanismo definitivo de almacenamiento será definido junto con el diseño de la base de datos.

## 6. Cierre de sesión

Cuando el usuario solicite cerrar sesión mediante:

```text
POST /api/v1/auth/logout
```

el backend deberá invalidar la sesión correspondiente.

Una sesión revocada no deberá permitir nuevamente el acceso a operaciones protegidas.

Esto permite cumplir con el requisito de HU04 relacionado con la invalidación de sesiones.

## 7. Autorización basada en roles

La autenticación y la autorización serán tratadas como responsabilidades relacionadas pero diferentes.

La autenticación permite determinar:

```text
¿Quién es el usuario?
```

Mientras que la autorización determina:

```text
¿Qué puede hacer este usuario?
```

Los roles principales serán:

```text
CLIENTE
PROVEEDOR
ADMINISTRADOR
```

La relación entre usuarios y roles se manejará mediante las estructuras correspondientes de la base de datos.

Además del rol, determinadas operaciones deberán validar la pertenencia del recurso al usuario o negocio correspondiente.

Por ejemplo, un proveedor no podrá acceder a recursos pertenecientes a otro proveedor aunque tenga el rol `PROVEEDOR`.

## 8. MFA para administradores

Las cuentas de administrador requerirán un mecanismo adicional de verificación cuando corresponda, de acuerdo con los requisitos de seguridad del proyecto.

Cuando un usuario sea promovido a `ADMINISTRADOR`, se deberá iniciar el proceso necesario para configurar el mecanismo de MFA.

Las operaciones sensibles también podrán requerir una verificación adicional.

La implementación específica del mecanismo MFA se definirá durante el desarrollo, manteniendo inicialmente una solución acorde con el alcance del proyecto.

## 9. Alternativas consideradas

### Sesiones tradicionales en servidor

Se consideró utilizar únicamente sesiones tradicionales administradas por el servidor.

Esta alternativa permitiría controlar directamente el estado de las sesiones, pero requiere mantener la información de autenticación del usuario en el servidor y resulta menos adecuada para la estrategia de API definida para el proyecto.

### JWT sin control de revocación

También se consideró utilizar únicamente JWT con una duración de una hora.

Aunque esta alternativa es sencilla, no permite controlar adecuadamente la revocación de una sesión antes de su expiración.

Por esta razón, se considera necesario complementar JWT con un mecanismo de control de sesiones.

### OAuth2 / OpenID Connect mediante un proveedor externo

Esta alternativa permitiría delegar parte de la gestión de identidad a un proveedor externo.

Sin embargo, para el alcance inicial del proyecto introduciría dependencias y configuración adicional que no son necesarias para las funcionalidades del Sprint 1.

Por esta razón, se utilizará inicialmente Spring Security con autenticación propia.

## 10. Justificación

La combinación de Spring Security, JWT y control de sesiones permite mantener una solución relativamente sencilla y adecuada para el tamaño del proyecto.

La decisión permite:

* Identificar usuarios en solicitudes protegidas.
* Aplicar RBAC.
* Controlar la expiración de sesiones.
* Revocar sesiones.
* Integrarse con los módulos del monolito.
* Mantener separadas autenticación y autorización.
* Implementar posteriormente MFA.
* Evitar una infraestructura de autenticación externa innecesaria.

## 11. Consecuencias positivas

* Integración directa con Spring Boot.
* Soporte para autenticación y autorización.
* Control de expiración.
* Posibilidad de revocar sesiones.
* Facilita la protección de endpoints.
* Permite implementar las reglas de HU02, HU04 y HU06.
* Mantiene la solución relativamente sencilla para el proyecto.

## 12. Consecuencias negativas

* Se requiere mantener información de control de sesiones en la base de datos.
* La implementación de revocación agrega cierta complejidad respecto a utilizar únicamente JWT.
* Será necesario definir correctamente la expiración y validación de los tokens.
* La implementación de MFA requerirá componentes adicionales.

## 13. Consideraciones de seguridad

La implementación deberá cumplir como mínimo con las siguientes condiciones:

* Las contraseñas deberán almacenarse mediante un algoritmo de hash seguro.
* Las contraseñas nunca deberán almacenarse en texto plano.
* Las contraseñas no deberán aparecer en logs.
* Los tokens completos no deberán registrarse en auditoría.
* Los tokens deberán tener una duración máxima de una hora.
* Las sesiones revocadas deberán ser rechazadas.
* Los endpoints protegidos deberán validar autenticación y autorización.
* Las cuentas administrativas deberán contar con MFA de acuerdo con los requisitos del proyecto.
* Las credenciales y secretos de configuración no deberán almacenarse directamente en el código fuente.

## 14. Historias de usuario relacionadas

Esta decisión afecta principalmente a:

* **HU02 — Inicio de sesión**
* **HU04 — Cerrar sesión**
* **HU05 — Gestionar roles y permisos**
* **HU06 — Acceso según rol**

También proporciona la base de autenticación necesaria para las funcionalidades que serán desarrolladas en los siguientes sprints.
