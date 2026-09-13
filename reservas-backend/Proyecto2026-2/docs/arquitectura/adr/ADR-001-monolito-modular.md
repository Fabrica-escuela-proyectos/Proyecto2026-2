# ADR-001 — Elección de arquitectura de monolito modular

* **Estado:** Aceptado
* **Decisión:** Utilizar una arquitectura de monolito modular para el backend de la Plataforma de Reservas de Servicios.

## 1. Contexto

La Plataforma de Reservas de Servicios requiere funcionalidades relacionadas con usuarios, proveedores, servicios, recursos, reservas, reportes y auditoría.

Aunque estas funcionalidades corresponden a diferentes dominios, mantienen relaciones entre sí y requieren compartir información y servicios transversales como autenticación, autorización y auditoría.

El proyecto será desarrollado por un equipo universitario pequeño y cuenta con un tiempo limitado para la implementación de los diferentes sprints. Por esta razón, se busca una arquitectura que permita mantener una separación clara de responsabilidades sin introducir una complejidad innecesaria.

## 2. Decisión

Se utilizará una arquitectura de **monolito modular** para el backend.

La aplicación será desplegada inicialmente como un único backend desarrollado con Spring Boot, pero estará dividida internamente en módulos correspondientes a los principales dominios funcionales:

* Identity & Access
* Provider
* Service
* Resource
* Reservation
* Report
* Audit

Cada módulo tendrá responsabilidades definidas y utilizará una estructura interna organizada en capas de `controller`, `application`, `domain` e `infrastructure`, según corresponda.

La comunicación entre módulos se realizará mediante servicios e interfaces internas, evitando que un módulo acceda directamente a las estructuras internas de otro módulo.

La aplicación utilizará PostgreSQL como base de datos relacional común.

## 3. Alternativas consideradas

### Microservicios

Se consideró una arquitectura basada en microservicios, separando los principales dominios en servicios independientes.

Esta alternativa fue descartada para la primera versión debido a que aumentaría la complejidad de desarrollo, despliegue, comunicación entre servicios, configuración y monitoreo.

Además, el proyecto no presenta actualmente una necesidad que justifique distribuir los módulos en diferentes servicios.

### Monolito tradicional

También se consideró un monolito sin separación modular.

Esta alternativa simplificaría inicialmente la implementación, pero podría generar un mayor acoplamiento entre las diferentes funcionalidades del sistema y dificultar su evolución.

Por esta razón, se considera preferible mantener una separación interna mediante módulos.

## 4. Justificación

El monolito modular permite mantener un único despliegue y una infraestructura relativamente sencilla, mientras proporciona separación entre los diferentes dominios funcionales.

Esta decisión se considera adecuada debido a:

* Tamaño y características del proyecto.
* Equipo de desarrollo reducido.
* Tiempo limitado para los sprints.
* Relación entre los diferentes dominios.
* Necesidad de compartir autenticación, autorización y auditoría.
* Menor complejidad de despliegue.
* Facilidad para realizar pruebas e integración.
* Posibilidad de evolucionar los módulos posteriormente.

La decisión no impide que un módulo pueda separarse en el futuro si las necesidades del sistema lo justifican.

## 5. Consecuencias positivas

* Menor complejidad de despliegue.
* Facilita el desarrollo dentro del tiempo disponible.
* Permite separar responsabilidades.
* Reduce el acoplamiento entre dominios.
* Facilita las pruebas y el mantenimiento.
* Permite compartir servicios transversales.
* Mantiene una estructura preparada para futuras modificaciones.

## 6. Consecuencias negativas

* Todos los módulos utilizan inicialmente el mismo proceso de ejecución.
* Un error grave en el backend puede afectar a toda la aplicación.
* Los módulos comparten inicialmente la infraestructura de despliegue.
* La separación entre módulos depende de respetar las interfaces y límites definidos en la arquitectura.

## 7. Aplicación al proyecto

La decisión se aplicará desde el Sprint 1 mediante la estructura modular definida para el backend.

Los módulos principales serán:

```text
Identity & Access
Provider
Service
Resource
Reservation
Report
Audit
```

Durante el Sprint 1 se desarrollarán principalmente las funcionalidades relacionadas con `Identity & Access`, junto con los componentes necesarios de `Provider` y `Audit`.

Los demás módulos quedarán preparados arquitectónicamente para su desarrollo en los siguientes sprints.

## 8. Revisión de la decisión

La decisión podrá ser revisada en futuros sprints si aparecen necesidades que justifiquen una arquitectura diferente, como requerimientos de escalabilidad independiente, despliegue independiente de módulos o necesidades de distribución que no puedan resolverse adecuadamente dentro del monolito modular.
