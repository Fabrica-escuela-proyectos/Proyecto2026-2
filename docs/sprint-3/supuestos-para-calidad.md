# Supuestos para confirmar con Calidad / PO

**Para:** el equipo de Calidad y el PO. **De:** Arquitectura de Software. **Respondido por Calidad el 6 de noviembre** (comentarios en `supuestos-para-calidad.docx`; ver la columna Estado). Plazo original: 6 de noviembre, que es cuando Calidad termina de definir sus casos de prueba: si un caso se escribe con otra interpretación, en las pruebas del 17 aparecería como «bug».

**Cómo responder:** cada pregunta trae lo que **ya implementamos**. Basta con «Sí» o «No, debe ser…». Si una pregunta no se responde, queda como está. Las respuestas se anotan en la columna **Estado** (Confirmado / Cambiado / Pendiente, con fecha y quién respondió).

Solo están aquí los supuestos que cambian lo que ve el usuario. Las decisiones técnicas internas las ratifica el equipo de Arquitectura (ver §3).

## 1. Sprint 3 — confirmar esta semana

| # | Pregunta | Lo que está implementado (y desplegado) | Si la respuesta es «no» | Estado |
|---|---|---|---|---|
| P1 | ¿Las **HU-30 a HU-34** entran al Sprint 3? En Azure solo existe la HU-29 | Las seis HU de reportes (29 a 34) están hechas, probadas y desplegadas | Si entran: crear las historias en Azure y Arquisoft les cuelga sus tareas. Si no: quedan como adelanto | Pendiente (sin respuesta) |
| P2 (D7) | **HU-07:** ¿los tipos de negocio son `SALUD`, `BELLEZA`, `BIENESTAR`, `DEPORTE`, `EDUCACION`, `CONSULTORIA`, `ENTRETENIMIENTO` y `OTRO`? | Esa lista exacta; cualquier otro valor (p. ej. «Panadería», «123») → 400 «El tipo de negocio no es válido» | **Urgente:** la lista ya está en la base de datos; cambiarla exige una migración nueva | **Confirmado** — «se dejan los tipos de negocio existentes» (Calidad (A. Naranjo), 6-nov) |
| P3 (D8) | **HU-07:** ¿el teléfono del negocio son 10 dígitos, celular (`3XXXXXXXXX`) o fijo (`60XXXXXXXX`)? | Así; `abcde12345`, `12345` y `123` → 400 | **Urgente:** también está en la base de datos (migración nueva) | **Confirmado** — «son 10 dígitos» (Calidad (A. Naranjo), 6-nov) |
| P4 (D5) | **HU-27:** ¿completar una reserva **no** habilita todavía calificar o reseñar? | No hay calificación: ninguna HU la define | Sería una HU nueva (tabla y endpoints), no un ajuste | **Confirmado: sin calificación** — marcado «Denegada» con el comentario «está fuera del alcance previsto» (Calidad (A. Naranjo), 6-nov) |
| P5 (D12) | **HU-11 y HU-21:** ¿el «aviso» al cliente cuando se cancela su reserva puede ser el registro de auditoría, sin correo? | Queda como evento de auditoría, igual que en el Sprint 2 | Enviar correos es un componente nuevo | **Cambiado: se debe mandar correo** (comentario; Estado sin marcar) → **implementado el 2026-11-11** con Brevo en todas las cancelaciones que no hace el cliente (HU-11, 16, 21, 26 y 28), ADR-009 (Calidad (A. Naranjo), 6-nov) |
| P6 (D1) | **HU-29/30:** ¿la ocupación es minutos reservados (confirmadas + completadas) ÷ minutos disponibles × 100, con el horario semanal **vigente** de los recursos **activos** menos sus cierres? | Así, con un decimal; sin minutos disponibles el porcentaje sale vacío (`null`) | Cambio de horas en el cálculo; sin tocar la base | **Confirmado** — «sí, que quede así» (Calidad (A. Naranjo), 6-nov) |
| P7 (D10) | **HU-21:** ¿un cierre es un **día completo** o un **rango con fecha y hora** (puede abarcar varios días, máximo 366)? | Así; se puede eliminar mientras no haya empezado | Cambio de horas | **Aceptada «segunda opción»** — «es un rango con fecha y hora»; por aclarar si se quita el día completo, que el AC sí pide (Calidad (A. Naranjo), 6-nov) |
| P8 (D6) | **HU-07, 10 y 15:** ¿la edición es parcial (se envía solo lo que cambia) y una cadena vacía en un dato opcional lo **borra**? | Así; el nombre nunca puede quedar vacío (400) | Cambio de horas | **Confirmado** — vacío en opcional = queda vacío; en obligatorio = se conserva lo anterior (Calidad (A. Naranjo), 6-nov) |
| P9 (D3) | **Reportes:** ¿sin fechas se muestran los **últimos 30 días**, y el periodo máximo es de **366 días**? | Así; el periodo se cuenta por el día de inicio de la reserva | Cambio de minutos | **Confirmado** (Calidad (A. Naranjo), 6-nov) |
| P10 (D4) | **HU-27:** ¿una reserva solo se completa **cuando ya pasó su hora de fin**? | Así; antes → 409 | Cambio de minutos | **Confirmado** (Calidad (A. Naranjo), 6-nov) |
| P11 (D13) | **HU-34:** ¿«negocio activo» = negocio cuyo proveedor tiene la cuenta habilitada? | Así (el negocio no tiene un estado propio) | Cambio de minutos | **Confirmado** (Calidad (A. Naranjo), 6-nov) |

## 2. Sprint 2 — siguen sin confirmación formal

No hubo reportes de Calidad en contra durante las pruebas del Sprint 2, pero nunca se confirmaron por escrito. Son las que más cambiarían lo ya construido:

| # | Pregunta | Lo que está implementado | Si la respuesta es «no» | Estado |
|---|---|---|---|---|
| P12 (S-27) | Al eliminar una cuenta, ¿su **nombre y correo pueden seguir** en las reservas históricas que ve el proveedor? (dato personal) | Se conservan, para que el historial tenga sentido | Reemplazarlos por «Cliente eliminado»: un ajuste pequeño | **Cambiado: «Cliente eliminado»**, no mostrar datos de clientes eliminados (comentario; Estado sin marcar) → **implementado el 2026-11-11** (migración V15) (Calidad (A. Naranjo), 6-nov) |
| P13 (S-14) | «No dos reservas en el mismo horario»: ¿es **por recurso** (dos salas pueden atender a la vez el mismo servicio)? | Por recurso | Por servicio: una migración y cambiar la regla | **Confirmado: por recurso** (Calidad (A. Naranjo), 6-nov) |
| P14 (S-04, S-16) | ¿El catálogo (HU-13) exige sesión y la disponibilidad (HU-20) es pública? ¿Un administrador **no** gestiona negocios ni ve reservas ajenas? | Así | Cambios de una línea | **Confirmado** (Calidad (A. Naranjo), 6-nov) |
| P15 (S-19) | **HU-26:** ¿basta con que el horario cancelado por el proveedor quede **liberado** (sin opción de «deshabilitarlo»)? | Queda liberado. Desde el Sprint 3, para bloquear un horario existe el cierre puntual (HU-21) | — | Sin respuesta: queda como está |
| P16 (S-18) | **HU-25:** ¿el cliente cancela con al menos **1 hora** de antelación, y el proveedor sin esa regla (pero no una reserva ya iniciada)? | Así | Cambio de minutos | Sin respuesta: queda como está |
| P17 (S-22) | ¿La **autoeliminación** de la cuenta queda fuera (solo la hace el administrador)? | Fuera | Endpoint nuevo con contraseña y MFA | Sin respuesta: queda como está |
| P18 (S-28) | ¿El formato de error con `error`, `message`, `fields` y `traceId` es aceptable, sin renombrarlo a `errorCode`/`details` de los Lineamientos? | Así | Cambiaría la respuesta de todos los errores | Sin respuesta: queda como está |

El resto de supuestos del Sprint 2 (S-05 a S-13, S-15, S-17, S-20, S-21, S-23, S-24, S-29) son topes y detalles de validación que Calidad ya ejercitó en sus pruebas; el detalle está en [`decisiones-y-supuestos-sprint-2.md`](../arquitectura/decisiones-y-supuestos-sprint-2.md) §2.

## 3. No son para Calidad (los decide o ratifica Arquitectura)

| Tema | Quién |
|---|---|
| D2 módulo de reportes sin tablas (ADR-008), D9 desactivar servicio con el mismo patrón que HU-16, D11 cierres descontados del horario efectivo | Simon, Juan Esteban y Santiago (ratificar ADR-004..008) |
| S-25 sesión JWT de 1 h, S-26 política de MFA, S-30 Swagger encendido en Render, S-32 despliegue en Render + Neon | Arquitectura |
| S-31 convenciones de la base (UUID, `ON DELETE`) | BD (Andraus) |

Detalle técnico de cada decisión: [`plan-de-trabajo-sprint-3.md`](plan-de-trabajo-sprint-3.md) §3 y [`decisiones-y-supuestos-sprint-2.md`](../arquitectura/decisiones-y-supuestos-sprint-2.md).
