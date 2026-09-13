# ADR-003 — Separación de módulos e interfaces

* **Estado:** Aceptado
* **Decisión:** Separar los módulos del backend mediante límites definidos y utilizar interfaces o servicios para la comunicación entre módulos.

## 1. Contexto

La plataforma de reservas está organizada como un monolito modular compuesto por diferentes módulos funcionales, entre ellos Identity & Access, Provider, Service, Resource, Reservation, Report y Audit.

Aunque todos los módulos forman parte de la misma aplicación Spring Boot y comparten la misma base de datos PostgreSQL, cada uno tiene responsabilidades diferentes.

Se requiere evitar que los módulos tengan un acoplamiento innecesario, especialmente mediante el acceso directo a repositorios, entidades o clases internas pertenecientes a otro módulo.

## 2. Decisión

Cada módulo tendrá una estructura interna separada en las capas `controller`, `application`, `domain` e `infrastructure`.

La comunicación entre módulos se realizará principalmente mediante interfaces y servicios definidos en la capa de aplicación, evitando que un módulo acceda directamente a los detalles internos de otro.

Por ejemplo, el módulo Reservation no deberá acceder directamente al `UserRepository` del módulo Identity & Access. En su lugar, podrá utilizar una interfaz o servicio como `IdentityService` para obtener la información necesaria del usuario.

Algunas interfaces o servicios principales serán:

* `IdentityService`: operaciones relacionadas con la identidad de los usuarios.
* `AuthorizationService`: validación de roles, permisos y acceso a recursos.
* `AuditService`: registro de eventos relevantes del sistema.

La persistencia también se mantendrá separada por módulo, aunque todos utilicen la misma base de datos PostgreSQL.

## 3. Alternativas consideradas

### 3.1 Acceso directo entre módulos

Cada módulo podría acceder directamente a los repositorios, entidades y clases internas de otros módulos.

Esta alternativa es sencilla inicialmente, pero genera mayor acoplamiento y dificulta modificar o probar los módulos de forma independiente.

**Se descarta** porque puede hacer que la separación definida para el monolito modular pierda efectividad.

### 3.2 Comunicación mediante servicios independientes

Otra posibilidad sería convertir cada módulo en un servicio independiente y utilizar comunicación mediante HTTP u otros mecanismos distribuidos.

**Se descarta** para el proyecto actual debido a que aumentaría considerablemente la complejidad de desarrollo, despliegue y mantenimiento, sin ser necesario para las necesidades actuales de la plataforma.

### 3.3 Servicio centralizado para toda la aplicación

También podría utilizarse un único servicio encargado de coordinar todas las operaciones de los diferentes módulos.

**Se descarta** porque concentraría demasiadas responsabilidades en un solo componente y aumentaría el acoplamiento entre funcionalidades.

## 4. Justificación

La separación mediante interfaces permite mantener los límites definidos entre los módulos sin dejar de aprovechar las ventajas de un monolito modular.

Esta decisión facilita:

* Mantener responsabilidades claras.
* Reducir el acoplamiento entre módulos.
* Facilitar las pruebas unitarias e integración.
* Cambiar implementaciones internas sin afectar a otros módulos.
* Mantener una estructura organizada durante los siguientes sprints.
* Permitir una futura evolución de determinados módulos si el proyecto lo requiere.

La decisión también es coherente con la estructura de paquetes y el diagrama de componentes definidos para el Sprint 1.

## 5. Consecuencias

### Positivas

* Mayor separación de responsabilidades.
* Menor dependencia entre implementaciones internas.
* Código más fácil de mantener.
* Mayor facilidad para realizar pruebas.
* Facilita la evolución de los módulos durante los siguientes sprints.

### Negativas

* Requiere definir interfaces adicionales.
* Puede aumentar ligeramente la cantidad de clases y código.
* Se debe controlar que los módulos respeten los límites establecidos.

## 6. Ejemplo de comunicación entre módulos

Un ejemplo de esta decisión se presenta cuando el módulo Reservation necesita validar la identidad o el acceso de un usuario.

En lugar de realizar directamente una consulta al repositorio interno de Identity & Access:

`Reservation → UserRepository`

se utilizará una abstracción del módulo correspondiente:

`Reservation → IdentityService`

De esta manera, Reservation conoce la operación que necesita, pero no depende directamente de la implementación utilizada para almacenar o recuperar los usuarios.

## 7. Relación con el Sprint 1

Esta decisión aplica principalmente a las funcionalidades de identidad, autenticación, autorización y auditoría implementadas durante el Sprint 1.

También establece las reglas de comunicación que deberán seguir los módulos que serán desarrollados en los siguientes sprints, especialmente Provider, Service, Resource, Reservation y Report.

La aplicación de esta decisión será revisada durante la implementación para verificar que los módulos mantengan los límites definidos y que no existan dependencias directas innecesarias entre sus componentes internos.
