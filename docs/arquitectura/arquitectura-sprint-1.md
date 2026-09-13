# Arquitectura de Software — Sprint 1

## 1. Introducción

El presente documento describe las decisiones y definiciones iniciales de arquitectura de software para el desarrollo del **Caso 14 — Plataforma de Reservas de Servicios**, correspondiente al equipo avanzado presencial 04 de CodeF@ctory UdeA.

La plataforma tiene como propósito permitir la gestión de reservas de servicios ofrecidos por diferentes tipos de establecimientos, como clínicas, consultorios, salones y centros deportivos. El sistema contempla funcionalidades relacionadas con usuarios, proveedores de servicios, servicios ofrecidos, horarios, disponibilidad, recursos y reservas.

Para el desarrollo del proyecto se propone una arquitectura que permita mantener una estructura organizada y modular, facilitando la evolución del sistema durante los diferentes sprints. Debido al tamaño del equipo, el tiempo disponible y las características del proyecto, se utilizará un **monolito modular**, separando las funcionalidades del sistema mediante módulos relacionados con los diferentes dominios del negocio.

Durante el Sprint 1 se trabajará principalmente la **Épica 0 — Identidad y acceso**, debido a que la autenticación, autorización y control de acceso son componentes transversales que serán utilizados por las demás funcionalidades de la plataforma.

## 2. Alcance del Sprint 1

El alcance del Sprint 1 comprende el establecimiento de las bases técnicas y estructurales del sistema, junto con la implementación de las funcionalidades correspondientes a la **Épica 0 — Identidad y acceso**.

Durante este sprint se desarrollarán las siguientes historias de usuario:

* **HU 01 — Registrar cliente:** permite a un usuario crear una cuenta como cliente mediante el registro de sus datos personales y credenciales de acceso.
* **HU 02 — Inicio de sesión:** permite a un usuario registrado autenticarse y acceder a las funcionalidades correspondientes a su rol.
* **HU 03 — Registro de proveedor de servicios:** permite registrar un proveedor y asociarlo con la información de su negocio, asignándole el rol correspondiente de manera controlada.
* **HU 04 — Cerrar sesión:** permite invalidar la sesión activa de un usuario y evitar el acceso posterior mediante dicha sesión.
* **HU 05 — Gestionar roles y permisos:** permite al administrador gestionar los roles y permisos de los usuarios de acuerdo con las restricciones definidas por el sistema.
* **HU 06 — Acceso según rol:** controla que cada usuario pueda acceder únicamente a las funcionalidades permitidas para su rol y, cuando corresponda, a los recursos que sean de su propiedad o pertenezcan a su negocio.

Estas historias de usuario permitirán establecer el módulo transversal de **Identidad y Acceso**, incluyendo autenticación, autorización, gestión de roles, control de sesiones, seguridad y auditoría de acciones críticas.

Como estrategia de implementación, la **HU 01 — Registrar cliente** será utilizada como primera funcionalidad de referencia para validar la estructura inicial del backend, la conexión con la base de datos, las validaciones, la persistencia y el manejo seguro de las credenciales. Posteriormente, la misma base arquitectónica será utilizada para implementar y validar las HU 02 a HU 06.

El alcance del sprint también contempla la preparación de la estructura inicial del proyecto, la documentación de las decisiones arquitectónicas, la integración con la base de datos, las pruebas correspondientes y un despliegue inicial que permita verificar el funcionamiento de la solución.

## 3. Objetivo arquitectónico del Sprint 1

El objetivo principal del Sprint 1 es establecer una base arquitectónica funcional, segura y extensible sobre la cual puedan desarrollarse las funcionalidades de los siguientes sprints.

La arquitectura deberá permitir:

* Separar las responsabilidades de los diferentes dominios del sistema.
* Mantener un código organizado y fácil de modificar.
* Implementar autenticación y autorización de los usuarios.
* Controlar el acceso a las funcionalidades mediante roles y permisos.
* Proteger la información relacionada con las credenciales y las sesiones.
* Implementar las restricciones de acceso según el rol y la pertenencia de los recursos.
* Registrar eventos relevantes para auditoría.
* Facilitar la integración entre el backend y la base de datos.
* Permitir la incorporación progresiva de nuevas funcionalidades.
* Mantener una estructura adecuada para las pruebas y el despliegue.

La arquitectura propuesta será refinada durante el desarrollo de los siguientes sprints de acuerdo con las necesidades que surjan del sistema, manteniendo las decisiones fundamentales que permitan conservar la separación de responsabilidades y la evolución controlada de la plataforma.

## 4. Requisitos y restricciones arquitectónicas

La arquitectura propuesta debe responder tanto a los requisitos funcionales definidos para el Sprint 1 como a los requisitos técnicos, de seguridad, calidad y operación establecidos para el proyecto.

### 4.1 Requisitos funcionales con impacto arquitectónico

Las historias de usuario de la Épica 0 — Identidad y acceso establecen requisitos que afectan directamente el diseño de la solución:

* El sistema debe permitir el registro de clientes y proveedores.
* El sistema debe permitir la autenticación de usuarios registrados.
* Los usuarios deben contar con un rol que determine las funcionalidades a las que pueden acceder.
* El sistema debe diferenciar como mínimo los roles **Cliente, Proveedor y Administrador**.
* El acceso de los proveedores debe estar restringido a los recursos y funcionalidades correspondientes a su propio negocio.
* El sistema debe impedir que un usuario modifique sus propios permisos o rol.
* Un proveedor no debe poder asignarse a sí mismo un rol privilegiado.
* Las cuentas administrativas deben contar con un mecanismo adicional de verificación mediante MFA.
* Las sesiones autenticadas deben tener un tiempo máximo de duración de **1 hora**.
* El cierre de sesión debe permitir invalidar la sesión del usuario.
* Las operaciones sensibles deben requerir una verificación adicional cuando corresponda.
* Las acciones críticas relacionadas con usuarios, roles, permisos y autenticación deben quedar registradas para efectos de auditoría.
* La información sensible, especialmente las contraseñas, no debe almacenarse ni registrarse de forma legible.

### 4.2 Requisitos de seguridad

La arquitectura debe incorporar mecanismos de seguridad desde el diseño y no únicamente como una etapa posterior de implementación.

Entre los principales requisitos se consideran:

* Almacenamiento seguro de contraseñas mediante mecanismos de hash adecuados.
* Validación de los datos recibidos por el backend.
* Control de acceso basado en roles y permisos.
* Verificación de pertenencia o propiedad de los recursos cuando sea necesaria.
* Protección de las sesiones y de la información utilizada para identificar al usuario.
* Expiración y revocación de sesiones o tokens.
* MFA para el acceso administrativo y operaciones que lo requieran.
* Protección frente a intentos repetitivos o abusivos de registro y autenticación.
* Prevención de vulnerabilidades comunes, incluyendo inyección y problemas relacionados con autenticación y autorización.
* Protección de secretos y credenciales utilizados por la aplicación.
* Registro de eventos de seguridad sin almacenar contraseñas u otra información sensible innecesaria.

### 4.3 Requisitos de calidad y rendimiento

La solución debe considerar los requisitos no funcionales establecidos para el proyecto.

Como referencia inicial, la arquitectura deberá soportar al menos **200 solicitudes por minuto** y considerar un tiempo máximo de respuesta de **30 segundos** como objetivo general establecido para el sistema.

Además, se deberán establecer y validar objetivos más específicos para las operaciones críticas cuando sea necesario.

La arquitectura también debe facilitar:

* Pruebas unitarias e integración.
* Revisión automática de calidad del código.
* Control de vulnerabilidades.
* Mantenimiento y evolución del sistema.
* Registro estructurado de eventos relevantes.
* Observabilidad progresiva durante los siguientes sprints.

### 4.4 Requisitos de persistencia y base de datos

El sistema utilizará **PostgreSQL** como base de datos relacional.

La arquitectura de persistencia deberá considerar:

* Modelo de datos normalizado.
* Claves primarias y foráneas.
* Restricciones de integridad.
* Índices justificados de acuerdo con las consultas relevantes.
* Migraciones versionadas.
* Consultas parametrizadas y mecanismos que eviten inyecciones SQL.
* Control de acceso mediante privilegios mínimos.
* Registro de cambios relevantes para auditoría.
* Estimación del volumen de datos y crecimiento esperado.
* Procedimientos o triggers únicamente cuando exista una justificación arquitectónica o de negocio.

Las decisiones específicas del modelo de datos serán desarrolladas y documentadas en conjunto con el diseño de Bases de Datos.

### 4.5 Restricciones tecnológicas

La solución deberá alinearse con el perfil tecnológico definido para el proyecto.

Para el backend se utilizará **Spring Boot**, mientras que la persistencia se realizará sobre PostgreSQL. La comunicación entre frontend y backend se realizará mediante una API, utilizando inicialmente un enfoque **REST**.

El sistema deberá contar con documentación de la API y mantener contratos claros entre los componentes.

Para el despliegue y ejecución del sistema se considerará el uso de contenedores mediante Docker y un servicio de despliegue en la nube, de acuerdo con las necesidades y posibilidades del proyecto.

### 4.6 Restricciones del proyecto

La solución debe ser viable para el tamaño del equipo y el tiempo disponible para el desarrollo.

Por esta razón, se priorizará una arquitectura que:

* Evite complejidad innecesaria.
* Permita desarrollar y probar las funcionalidades de manera independiente dentro de la misma aplicación.
* Facilite el trabajo paralelo entre los integrantes del equipo.
* Permita incorporar progresivamente las funcionalidades de los siguientes sprints.
* Mantenga una separación clara de responsabilidades.
* Sea suficientemente robusta para cumplir los requisitos de seguridad y calidad sin introducir infraestructura innecesaria.

Estas restricciones justifican la selección de un **monolito modular** como estilo arquitectónico inicial del sistema.

## 5. Estilo arquitectónico

Para el desarrollo de la plataforma se utilizará un **monolito modular** como estilo arquitectónico principal. La aplicación será desplegada inicialmente como una única unidad, pero estará organizada internamente mediante módulos que separen las diferentes responsabilidades y dominios del negocio.

Esta decisión busca mantener un equilibrio entre organización, mantenibilidad y complejidad técnica. La plataforma contiene diferentes funcionalidades relacionadas entre sí, como identidad, proveedores, recursos y reservas, que requieren comunicación y acceso coordinado a la información del sistema. Al mismo tiempo, el tamaño del equipo y el tiempo disponible hacen conveniente evitar la complejidad operativa asociada con una arquitectura distribuida.

### 5.1 Justificación del monolito modular

El monolito modular permite que los componentes del sistema se encuentren dentro de una misma aplicación, manteniendo una separación lógica entre los diferentes módulos.

Esta alternativa resulta adecuada para el proyecto por las siguientes razones:

* Reduce la complejidad de despliegue y configuración al mantener inicialmente una única aplicación.
* Facilita el desarrollo y las pruebas por parte de un equipo pequeño.
* Permite compartir mecanismos transversales como autenticación, autorización y auditoría.
* Evita introducir comunicación de red entre múltiples servicios cuando no existe una necesidad funcional para ello.
* Permite separar las responsabilidades del sistema mediante módulos independientes.
* Facilita la evolución del proyecto durante los siguientes sprints.
* Permite mantener una estructura que podría ser evolucionada posteriormente si las necesidades del sistema justifican una arquitectura distribuida.

El uso de un monolito modular no implica que todas las funcionalidades estén mezcladas. Cada módulo deberá mantener responsabilidades claras y utilizar interfaces o servicios internos definidos para comunicarse con otros módulos.

### 5.2 Separación por módulos

La aplicación se organizará inicialmente alrededor de los principales dominios funcionales de la plataforma:

```text
Backend
│
├── Identidad y acceso
├── Proveedores
├── Servicios
├── Recursos
├── Reservas
├── Reportes
└── Auditoría
```

Durante el Sprint 1 se desarrollará principalmente el módulo de **Identidad y acceso**, junto con los componentes necesarios para auditoría y la infraestructura requerida para soportar las historias de usuario de la Épica 0.

Los demás módulos serán incorporados progresivamente en los siguientes sprints, manteniendo la misma estructura arquitectónica.

### 5.3 Separación de responsabilidades

Dentro de cada módulo se mantendrá una separación entre las responsabilidades relacionadas con la entrada de solicitudes, la lógica de aplicación, las reglas de negocio y la persistencia.

De forma general, se utilizará una estructura similar a:

```text
Módulo
│
├── Controller / API
│       └── Recibe y valida solicitudes
│
├── Application / Service
│       └── Coordina los casos de uso
│
├── Domain
│       └── Contiene reglas y conceptos del negocio
│
└── Infrastructure / Persistence
        └── Maneja acceso a la base de datos
```

Esta separación busca evitar que los controladores contengan directamente las reglas de negocio o que el acceso a la base de datos quede mezclado con la lógica de las funcionalidades.

### 5.4 Comunicación entre módulos

Los módulos se comunicarán dentro de la misma aplicación mediante servicios e interfaces internas, evitando dependencias innecesarias entre sus detalles de implementación.

Por ejemplo, el módulo de reservas podrá utilizar las capacidades de autenticación y autorización proporcionadas por el módulo de identidad sin acceder directamente a sus detalles internos.

La comunicación seguirá una dirección controlada entre responsabilidades, buscando que cada módulo exponga únicamente las operaciones necesarias para otros módulos.

### 5.5 Tecnologías principales

La implementación del backend seguirá el perfil tecnológico definido para el proyecto:

* **Spring Boot** para el backend.
* **Java** como lenguaje de programación.
* **Spring Security** para los mecanismos de autenticación y autorización.
* **PostgreSQL** como sistema de gestión de base de datos.
* **REST** como mecanismo inicial de comunicación entre frontend y backend.
* **Docker** para facilitar la ejecución y despliegue de la aplicación.

La selección específica de mecanismos de autenticación, manejo de sesiones, estructura de paquetes y persistencia será detallada en las siguientes secciones del documento.

## 6. Arquitectura propuesta

La solución se implementará como un **monolito modular**, organizado por dominios funcionales. La aplicación estará contenida inicialmente en un único backend, pero sus responsabilidades estarán separadas mediante módulos con límites y responsabilidades definidos.

La arquitectura propuesta busca que cada módulo sea responsable de un conjunto específico de funcionalidades y que la comunicación entre módulos se realice mediante interfaces y servicios internos, evitando que un módulo dependa directamente de los detalles internos de otro.

### 6.1 Vista general

La estructura general de la solución se plantea de la siguiente manera:

```text
                         PLATAFORMA
                              │
             ┌────────────────┴────────────────┐
             │                                 │
        Frontend                            Backend
           N/A                             Spring Boot
                                               │
              ┌────────────────────────────────┼────────────────────────┐
              │                │                │             │           │
              ▼                ▼                ▼             ▼           ▼
        Identidad        Proveedores        Servicios      Recursos    Reservas
        y Acceso
              │
              └──────────────────┐
                                 ▼
                            Auditoría
                                 │
                                 ▼
                             PostgreSQL
```

Esta vista representa la organización lógica inicial del backend. Todos los módulos forman parte de la misma aplicación, pero mantienen responsabilidades diferenciadas.

### 6.2 Módulo de Identidad y Acceso

El módulo de **Identidad y Acceso** será el módulo principal del Sprint 1 y tendrá responsabilidad sobre las funcionalidades relacionadas con la identificación y autorización de los usuarios.

Entre sus responsabilidades estarán:

* Registro de clientes.
* Registro de proveedores.
* Inicio de sesión.
* Cierre de sesión.
* Gestión de roles.
* Gestión de permisos.
* Control de acceso según rol.
* Control de sesiones.
* Integración con mecanismos de MFA para cuentas administrativas.
* Aplicación de restricciones relacionadas con autenticación y autorización.

Este módulo será utilizado por los demás módulos cuando necesiten determinar quién realiza una operación y si tiene autorización para ejecutarla.

### 6.3 Módulo de Proveedores

El módulo de **Proveedores** será responsable de la información y funcionalidades relacionadas con los proveedores de servicios y sus negocios.

Entre sus responsabilidades futuras estarán:

* Información del proveedor.
* Información del negocio asociado.
* Servicios ofrecidos por el proveedor.
* Relación entre proveedor y negocio.
* Gestión de la información que corresponda al establecimiento.

Durante el Sprint 1 se implementará únicamente la parte necesaria para soportar la **HU 03 — Registro de proveedor de servicios**, manteniendo preparada la estructura para las funcionalidades posteriores.

### 6.4 Módulo de Servicios

El módulo de **Servicios** tendrá como responsabilidad gestionar los servicios que los proveedores ofrecen a los clientes.

Entre sus responsabilidades futuras estarán:

* Crear y modificar servicios.
* Consultar servicios disponibles.
* Asociar servicios con proveedores o negocios.
* Definir características propias del servicio.

Este módulo será desarrollado progresivamente en los siguientes sprints.

### 6.5 Módulo de Recursos

El módulo de **Recursos** será responsable de representar y gestionar los recursos necesarios para prestar un servicio.

Dependiendo del tipo de establecimiento, un recurso puede representar, por ejemplo, un consultorio, una sala, una cancha, una estación de atención u otro espacio necesario para realizar una reserva.

Entre sus responsabilidades futuras estarán:

* Registrar recursos.
* Asociar recursos con un negocio.
* Consultar disponibilidad de recursos.
* Controlar el acceso del proveedor únicamente a los recursos de su negocio.

Este último punto se relacionará posteriormente con las reglas de autorización definidas en la arquitectura.

### 6.6 Módulo de Reservas

El módulo de **Reservas** será responsable de gestionar el ciclo de vida de las reservas realizadas por los clientes.

Entre sus responsabilidades futuras estarán:

* Crear reservas.
* Consultar reservas.
* Cancelar reservas.
* Validar disponibilidad.
* Relacionar una reserva con un usuario, servicio y recurso.
* Mantener el historial de reservas.

Este módulo utilizará los mecanismos de Identidad y Acceso para determinar el usuario que realiza una operación y validar los permisos correspondientes.

### 6.7 Módulo de Reportes

El módulo de **Reportes** tendrá como responsabilidad generar información agregada relacionada con el uso de la plataforma.

Entre sus posibles responsabilidades estarán:

* Reportes de reservas.
* Información de ocupación.
* Estadísticas de utilización.
* Consultas agregadas para proveedores y administradores.

Su implementación será desarrollada en los sprints posteriores de acuerdo con las historias de usuario correspondientes.

### 6.8 Módulo de Auditoría

El módulo de **Auditoría** será responsable de registrar eventos relevantes para la trazabilidad y seguridad del sistema.

Se considerarán como eventos de auditoría, entre otros:

* Registro de usuarios.
* Inicio y cierre de sesión.
* Cambios de roles o permisos.
* Intentos relevantes de acceso.
* Operaciones administrativas críticas.
* Eventos relacionados con MFA.

El módulo de auditoría no almacenará contraseñas ni información sensible innecesaria. Su propósito será proporcionar trazabilidad sobre las acciones relevantes realizadas dentro de la plataforma.

La auditoría tendrá un carácter transversal, por lo que podrá recibir eventos provenientes de diferentes módulos.

### 6.9 Relación entre los módulos

La comunicación entre los módulos se realizará mediante servicios e interfaces internas de la aplicación.

De forma general, las relaciones principales serán:

```text
                    ┌──────────────────┐
                    │ Identidad y      │
                    │ Acceso           │
                    └────────┬─────────┘
                             │
                 autorización│identidad
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
          ▼                  ▼                  ▼
    Proveedores         Servicios          Reservas
          │                  │                  │
          │                  │                  ▼
          │                  │              Recursos
          │                  │                  │
          └──────────────────┴──────────────────┘
                             │
                             ▼
                         Auditoría
```

La representación anterior es conceptual. Las dependencias específicas entre módulos se definirán con mayor precisión durante el diseño de componentes y de las interfaces internas.

### 6.10 Persistencia

Los módulos utilizarán PostgreSQL como mecanismo común de persistencia, manteniendo separación lógica de responsabilidades.

El acceso a los datos se realizará desde la capa de infraestructura o persistencia correspondiente a cada módulo. Los módulos no deberán acceder directamente a las estructuras internas de persistencia de otros módulos.

Las entidades y relaciones definitivas de la base de datos serán definidas conjuntamente con el diseño de Bases de Datos, manteniendo consistencia entre el modelo arquitectónico y el modelo físico.

### 6.11 Alcance de los módulos durante el Sprint 1

Aunque la arquitectura contempla los módulos necesarios para la evolución completa de la plataforma, el desarrollo del Sprint 1 se concentrará en los componentes necesarios para implementar la Épica 0.

La distribución inicial será:

| Módulo             | Participación en Sprint 1                                |
| ------------------ | -------------------------------------------------------- |
| Identidad y Acceso | Desarrollo principal                                     |
| Proveedores        | Soporte para HU03                                        |
| Servicios          | Preparación arquitectónica                               |
| Recursos           | Preparación arquitectónica                               |
| Reservas           | Preparación arquitectónica                               |
| Reportes           | Preparación arquitectónica                               |
| Auditoría          | Implementación de capacidades necesarias para la Épica 0 |

Esta distribución permite diseñar la arquitectura considerando la evolución futura del sistema sin intentar implementar funcionalidades que pertenecen a otros sprints.

## 7. Estructura de módulos y paquetes

La estructura interna del backend seguirá el enfoque de monolito modular definido anteriormente. Cada dominio funcional contará con un módulo independiente dentro de la aplicación Spring Boot, manteniendo una separación clara de responsabilidades.

La organización propuesta será la siguiente:

```text
backend/
└── src/
    └── main/
        └── java/
            └── com.codefactory.reservas/
                │
                ├── identity/
                │   ├── controller/
                │   ├── application/
                │   ├── domain/
                │   └── infrastructure/
                │
                ├── provider/
                │   ├── controller/
                │   ├── application/
                │   ├── domain/
                │   └── infrastructure/
                │
                ├── service/
                │   ├── controller/
                │   ├── application/
                │   ├── domain/
                │   └── infrastructure/
                │
                ├── resource/
                │   ├── controller/
                │   ├── application/
                │   ├── domain/
                │   └── infrastructure/
                │
                ├── reservation/
                │   ├── controller/
                │   ├── application/
                │   ├── domain/
                │   └── infrastructure/
                │
                ├── report/
                │   ├── controller/
                │   ├── application/
                │   ├── domain/
                │   └── infrastructure/
                │
                └── audit/
                    ├── application/
                    ├── domain/
                    └── infrastructure/
```

La estructura anterior representa la organización arquitectónica propuesta y no implica que todos los paquetes deban contener clases desde el inicio. Los paquetes correspondientes a funcionalidades que serán desarrolladas en sprints posteriores podrán incorporarse progresivamente.

### 7.1 Responsabilidad de los paquetes

Dentro de cada módulo se utilizará una separación de responsabilidades.

#### Controller

Contendrá los componentes encargados de recibir las solicitudes provenientes de la API REST y devolver las respuestas correspondientes.

Sus responsabilidades principales serán:

* Recibir solicitudes HTTP.
* Validar la estructura de los datos de entrada.
* Invocar los casos de uso correspondientes.
* Transformar los resultados en respuestas HTTP.
* No contener reglas principales del negocio.

#### Application

Contendrá los casos de uso y servicios de aplicación que coordinan las operaciones del módulo.

Sus responsabilidades serán:

* Coordinar los pasos necesarios para ejecutar un caso de uso.
* Aplicar las validaciones y reglas correspondientes al flujo de aplicación.
* Coordinar la comunicación con otros componentes o módulos.
* Controlar las transacciones cuando corresponda.

#### Domain

Contendrá los conceptos y reglas propias del dominio del módulo.

Sus responsabilidades serán:

* Representar conceptos importantes del negocio.
* Contener reglas que pertenecen directamente al dominio.
* Mantener independientes las reglas principales de detalles tecnológicos cuando sea posible.

#### Infrastructure

Contendrá los componentes relacionados con detalles técnicos externos al dominio.

Entre ellos se encuentran:

* Repositorios y acceso a PostgreSQL.
* Implementaciones de persistencia.
* Configuraciones técnicas.
* Integraciones externas cuando sean necesarias.

### 7.2 Dependencias internas

Las dependencias dentro de cada módulo deberán mantener una dirección controlada.

De forma general:

```text
Controller
    │
    ▼
Application
    │
    ▼
Domain
    ▲
    │
Infrastructure
```

El objetivo es evitar que los controladores implementen directamente reglas de negocio o que las entidades del dominio dependan de detalles específicos de la infraestructura.

### 7.3 Dependencias entre módulos

Los módulos no deberán acceder directamente a las clases internas de otros módulos.

Cuando un módulo necesite una funcionalidad de otro, deberá utilizar una interfaz o servicio expuesto para dicha comunicación.

Por ejemplo:

```text
Reserva
   │
   │ necesita conocer al usuario autenticado
   ▼
Identidad y Acceso
   │
   └── expone una interfaz o servicio interno
```

De esta manera, el módulo de Reservas no necesita conocer cómo Identidad y Acceso almacena usuarios, contraseñas, roles o sesiones.

### 7.4 Estructura durante el Sprint 1

Durante el Sprint 1 se implementarán principalmente los paquetes necesarios para la Épica 0 — Identidad y acceso.

La estructura inicial tendrá especial atención en:

```text
identity/
├── controller/
├── application/
├── domain/
└── infrastructure/

audit/
├── application/
├── domain/
└── infrastructure/
```

El módulo de Proveedores tendrá la estructura mínima necesaria para soportar el registro de proveedores definido en la HU03.

Los demás módulos podrán contar inicialmente con su estructura base o ser incorporados durante los sprints en los que se desarrollen sus respectivas funcionalidades.

### 7.5 Criterio de evolución

La estructura de paquetes podrá ampliarse durante el proyecto cuando aparezcan nuevas reglas, casos de uso o integraciones. Sin embargo, las modificaciones deberán mantener la separación de responsabilidades y evitar que los módulos se conviertan en dependencias fuertemente acopladas.

La estructura arquitectónica será revisada durante la implementación para comprobar que las decisiones documentadas correspondan con la organización real del código.

## 8. Diagramas de arquitectura

Los diagramas de arquitectura permiten representar de forma visual la estructura propuesta para el backend de la plataforma y la relación entre sus diferentes módulos. Para el Sprint 1 se utilizan dos diagramas principales: el diagrama de paquetes y el diagrama de componentes.

El diagrama de paquetes representa la organización interna del código fuente, mientras que el diagrama de componentes representa la colaboración entre los módulos y los servicios transversales de la aplicación.

Los diagramas fueron diseñados considerando la arquitectura de monolito modular definida para el proyecto.

### 8.1 Diagrama de paquetes

El diagrama de paquetes representa la organización del backend desarrollado con Spring Boot. La aplicación se encuentra dividida en módulos correspondientes a los principales dominios funcionales del sistema.

Cada módulo de negocio se organiza en las capas `controller`, `application`, `domain` e `infrastructure`, siguiendo la separación definida en la arquitectura. El módulo de auditoría se maneja como un servicio transversal y no requiere un controlador propio.

Los módulos principales representados son:

* Identity & Access
* Provider
* Service
* Resource
* Reservation
* Report
* Audit

La comunicación interna de cada módulo sigue principalmente la dirección `Controller → Application → Domain`, mientras que la infraestructura se encarga de implementar los mecanismos relacionados con persistencia y otros detalles técnicos.

La persistencia utiliza PostgreSQL como base de datos relacional común para la aplicación. Cada módulo mantiene separadas sus responsabilidades de acceso a datos para evitar un acoplamiento directo con las estructuras internas de otros módulos.

**Diagrama de paquetes:**

El archivo editable del diagrama se encuentra en:

`docs/arquitectura/diagramas/diagrama-paquetes.puml`

### 8.2 Diagrama de componentes

El diagrama de componentes representa los principales componentes que conforman el backend y sus relaciones. A diferencia del diagrama de paquetes, este diagrama se enfoca en las responsabilidades funcionales y en los servicios que permiten la comunicación entre los diferentes módulos.

Los módulos de negocio definidos para la solución son:

* **Identity & Access:** gestiona el registro, autenticación, cierre de sesión, roles, permisos y control de acceso.
* **Provider:** gestiona la información relacionada con los proveedores y sus negocios.
* **Service:** representa los servicios ofrecidos por los proveedores.
* **Resource:** gestiona los recursos disponibles para las reservas.
* **Reservation:** gestiona las reservas y las relaciones entre servicios, recursos y usuarios.
* **Report:** permite obtener información y estadísticas relacionadas con las reservas y el uso del sistema.

Además, se identifican dos servicios transversales:

* **Authorization Service:** centraliza las operaciones relacionadas con autorización y control de permisos.
* **Audit Service:** registra eventos relevantes generados por los diferentes módulos, evitando almacenar contraseñas u otra información sensible innecesaria.

La persistencia se representa mediante el componente `Persistence / JPA`, encargado de establecer la comunicación entre los módulos de la aplicación y PostgreSQL. De esta manera, los detalles de almacenamiento permanecen separados de la lógica funcional de los módulos.

**Diagrama de componentes:**

El archivo editable del diagrama se encuentra en:

`docs/arquitectura/diagramas/diagrama-componentes.puml`

### 8.3 Relación entre los diagramas

Los dos diagramas representan diferentes vistas de una misma arquitectura.

El **diagrama de paquetes** muestra cómo se organiza internamente el código del backend y cómo se separan las responsabilidades mediante módulos y capas.

El **diagrama de componentes** muestra cómo esos módulos colaboran entre sí y cómo utilizan servicios transversales como autorización y auditoría, además del mecanismo de persistencia.

Esta separación permite mantener una arquitectura comprensible y facilita la evolución del sistema durante los siguientes sprints sin modificar innecesariamente la estructura de los módulos existentes.
