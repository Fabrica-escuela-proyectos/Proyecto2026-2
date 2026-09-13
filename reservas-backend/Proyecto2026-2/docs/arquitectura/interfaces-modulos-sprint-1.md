# Interfaces entre módulos — Sprint 1

## 1. Introducción

Debido a que la solución utiliza una arquitectura de monolito modular, los módulos se encuentran dentro de una misma aplicación Spring Boot, pero mantienen separadas sus responsabilidades.

Para evitar dependencias directas entre las clases internas de los módulos, la comunicación entre ellos se realizará mediante servicios e interfaces definidos por cada módulo.

Durante el Sprint 1 se establecen las interfaces principales relacionadas con identidad, autorización y auditoría.

---

## 2. Identity Service

El `Identity Service` pertenece al módulo **Identity & Access** y permite que otros módulos obtengan información básica relacionada con el usuario autenticado sin acceder directamente a las estructuras internas del módulo de identidad.

### Responsabilidades

* Obtener información básica del usuario autenticado.
* Consultar el identificador del usuario.
* Consultar el rol del usuario.
* Determinar si el usuario se encuentra autenticado.
* Proporcionar información necesaria para las validaciones de autorización.

### Ejemplo conceptual

```text
Otros módulos
      │
      ▼
IdentityService
      │
      ▼
Identity & Access
```

Los módulos externos no deberán acceder directamente a los repositorios o entidades internas de `Identity & Access`.

---

## 3. Authorization Service

El `Authorization Service` se encarga de centralizar las validaciones relacionadas con permisos y acceso.

### Responsabilidades

* Validar si un usuario tiene un determinado permiso.
* Validar si un usuario tiene un rol requerido.
* Participar en las validaciones de acceso a recursos.
* Aplicar las reglas generales de autorización.

### Ejemplo conceptual

```text
Reservation
     │
     ▼
AuthorizationService
     │
     ▼
¿Usuario autorizado?
     │
   Sí / No
```

El servicio de autorización no reemplaza las reglas específicas de cada módulo. Por ejemplo, un proveedor puede tener permiso para administrar recursos, pero el módulo correspondiente deberá comprobar además que el recurso pertenece a su propio negocio.

---

## 4. Audit Service

El `Audit Service` pertenece al módulo transversal de **Audit** y permite registrar eventos importantes generados por los diferentes módulos.

### Responsabilidades

* Registrar eventos de autenticación.
* Registrar registros de usuarios y proveedores.
* Registrar cambios de roles.
* Registrar cierres de sesión.
* Registrar accesos rechazados cuando sea necesario.
* Mantener trazabilidad de operaciones relevantes.

### Ejemplo conceptual

```text
Identity ────────┐
Provider ────────┤
Reservation ─────┤
                  ▼
             AuditService
                  │
                  ▼
             Audit Logs
```

Los módulos no deberán escribir directamente en las tablas de auditoría. En su lugar, utilizarán el servicio de auditoría.

La información registrada deberá excluir contraseñas, tokens completos y cualquier otra información sensible que no sea necesaria para la trazabilidad.

---

## 5. Relaciones entre módulos

Las principales relaciones definidas para el Sprint 1 son:

| Módulo origen     | Interfaz / servicio    | Módulo destino    | Propósito                                 |
| ----------------- | ---------------------- | ----------------- | ----------------------------------------- |
| Identity & Access | `AuditService`         | Audit             | Registrar eventos de identidad            |
| Identity & Access | `AuthorizationService` | Authorization     | Validar permisos y acceso                 |
| Provider          | `IdentityService`      | Identity & Access | Obtener información del usuario/proveedor |
| Provider          | `AuthorizationService` | Authorization     | Validar permisos                          |
| Provider          | `AuditService`         | Audit             | Registrar operaciones relevantes          |
| Reservation       | `IdentityService`      | Identity & Access | Identificar usuario                       |
| Reservation       | `AuthorizationService` | Authorization     | Validar permisos                          |
| Reservation       | `AuditService`         | Audit             | Registrar operaciones relevantes          |
| Resource          | `AuthorizationService` | Authorization     | Validar acceso a recursos                 |
| Resource          | `AuditService`         | Audit             | Registrar operaciones relevantes          |
| Service           | `AuthorizationService` | Authorization     | Validar permisos                          |
| Service           | `AuditService`         | Audit             | Registrar operaciones relevantes          |
| Report            | `AuthorizationService` | Authorization     | Validar acceso a reportes                 |

Estas relaciones representan dependencias lógicas y no implican que los módulos se comuniquen mediante servicios de red independientes. Todos forman parte de la misma aplicación Spring Boot.

---

## 6. Regla de dependencia entre módulos

Los módulos deberán evitar el acceso directo a las clases internas de otros módulos.

Por ejemplo, no se deberá realizar una dependencia como:

```text
Reservation
     │
     └──> UserRepository de Identity
```

En su lugar, se utilizará una interfaz o servicio:

```text
Reservation
     │
     └──> IdentityService
                │
                ▼
         Identity & Access
```

De esta forma, `Reservation` conoce únicamente la operación que necesita y no la implementación interna del módulo de identidad.

---

## 7. Comunicación dentro del monolito

La comunicación entre módulos será interna y se realizará mediante llamadas a servicios e interfaces de Java.

No se utilizarán llamadas HTTP entre módulos porque todos pertenecen al mismo backend.

La estructura conceptual será:

```text
┌─────────────────────┐
│ Módulo de negocio   │
└──────────┬──────────┘
           │
           ▼
     Interfaz / Service
           │
           ▼
┌─────────────────────┐
│ Módulo responsable  │
└─────────────────────┘
```

Esto permite mantener los límites de los módulos sin introducir la complejidad de una arquitectura de microservicios.

---

## 8. Interfaces prioritarias para Sprint 1

Las interfaces que deberán considerarse prioritarias durante la implementación inicial son:

### IdentityService

Permite consultar información básica del usuario autenticado.

Ejemplo conceptual:

```java
public interface IdentityService {

    UserIdentity getCurrentUser();

    boolean isAuthenticated();
}
```

### AuthorizationService

Permite realizar validaciones generales de autorización.

Ejemplo conceptual:

```java
public interface AuthorizationService {

    boolean hasRole(Long userId, String role);

    boolean hasPermission(Long userId, String permission);
}
```

### AuditService

Permite registrar eventos relevantes.

Ejemplo conceptual:

```java
public interface AuditService {

    void registerEvent(String eventType, Long userId);
}
```

Los ejemplos anteriores representan únicamente el contrato conceptual. Los nombres definitivos, parámetros y tipos de respuesta podrán ajustarse durante la implementación.

---

## 9. Aplicación a las historias de usuario del Sprint 1

Las interfaces definidas permiten implementar las historias de usuario de la siguiente manera:

### HU01 — Registrar cliente

`Identity & Access` crea el usuario y utiliza `AuditService` para registrar el evento.

```text
POST /api/v1/users
        │
        ▼
Identity & Access
        │
        ├── Crear usuario
        ├── Asignar CLIENTE
        │
        └── AuditService
```

### HU02 — Inicio de sesión

`Identity & Access` valida las credenciales y genera la sesión. Los eventos relevantes se registran mediante `AuditService`.

### HU03 — Registrar proveedor

`Provider` utiliza las funcionalidades de identidad para crear el usuario y posteriormente registra la operación mediante `AuditService`.

### HU04 — Cerrar sesión

`Identity & Access` revoca la sesión y registra el evento mediante `AuditService`.

### HU05 — Gestionar roles y permisos

La operación es realizada por un administrador autorizado. Se utiliza `AuthorizationService` para validar permisos y `AuditService` para registrar el cambio.

### HU06 — Acceso según rol

Los módulos protegidos utilizan `AuthorizationService` para validar los permisos generales y aplican adicionalmente las reglas específicas de pertenencia al recurso o negocio.

---

## 10. Beneficios de esta separación

La definición de interfaces permite:

* Reducir el acoplamiento entre módulos.
* Evitar el acceso directo a repositorios de otros módulos.
* Centralizar las validaciones de autorización.
* Centralizar la auditoría.
* Facilitar las pruebas unitarias.
* Permitir modificar la implementación interna de un módulo sin afectar directamente a los demás.
* Mantener la arquitectura preparada para evolucionar en futuros sprints.

La comunicación mediante interfaces mantiene la simplicidad del monolito modular sin introducir la complejidad adicional de microservicios.
