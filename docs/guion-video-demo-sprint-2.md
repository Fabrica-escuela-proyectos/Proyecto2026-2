# Guion paso a paso del video — Sprint 2 (para quien no conoce el proyecto)

**Duración:** 7 minutos · **Herramienta:** solo el navegador (Swagger) · **Qué se prueba:** la plataforma de reservas que construimos: alguien registra su negocio, un cliente reserva, y se prueban cancelaciones, un recurso desactivado y la eliminación de una cuenta.
Este guion se complementa con la [guía técnica](guia-video-demo-sprint-2.md) (mismos pasos y numeración, con más detalle). Aquí tienes **qué hacer, qué decir y qué HU estás probando** en cada paso.

> **HU** = historia de usuario (una funcionalidad que el cliente pidió). Cada paso dice cuál estás mostrando. Lo que dices son unas 440 palabras (≈ 3 minutos de voz); el resto de los 7 minutos son los clics y esperar cada respuesta, así que **no hace falta hablar de corrido**: habla mientras haces cada paso y calla mientras escribes.

## 1. Antes de grabar (10 minutos)

1. Abre `https://proyecto2026-2-5zoo.onrender.com/actuator/health` y **recarga hasta ver `{"status":"UP"}`** (si estaba dormida tarda hasta 2 minutos). Hazlo 5 minutos antes de grabar.
2. Abre **3 pestañas** con `https://proyecto2026-2-5zoo.onrender.com/swagger-ui.html` y ponles nombre en tu cabeza: **PROVEEDOR**, **CLIENTE** y **ADMIN**.
3. Abre un **bloc de notas** y ve pegando ahí cada `id` que aparezca (negocio, servicio, recurso, reservas, cliente, proveedor).
4. Elige una **fecha en día de semana** (lunes a viernes), de mañana en adelante, formato `AAAA-MM-DD`. Úsala en todos los pasos.
5. Escoge una **contraseña de demo** (mínimo 8 caracteres, con mayúscula, minúscula y un símbolo) y no la muestres en pantalla.
6. Tres pestañas más, para el inicio: el [diagrama de despliegue](https://github.com/Fabrica-escuela-proyectos/Proyecto2026-2/blob/main/docs/arquitectura/diagramas/diagrama-despliegue.png), [GitHub Actions](https://github.com/Fabrica-escuela-proyectos/Proyecto2026-2/actions) y [SonarCloud](https://sonarcloud.io/project/overview?id=Fabrica-escuela-proyectos_Proyecto2026-2).
7. La parte del administrador necesita **una cuenta administradora con la app autenticadora abierta en el celular** (Simon o quien ya la tenga).

**No hagas más de 2 tomas en 10 minutos** (el sistema limita a 5 registros por IP en ese tiempo, y esta demo usa 2).

## 2. Cómo se usa Swagger (lo único que necesitas saber)

| Acción | Cómo |
|---|---|
| Abrir una operación | Clic en la barra de color (por ejemplo, el verde **POST**). Se despliega |
| Probarla | Botón **Try it out** (arriba a la derecha de la operación) |
| Poner datos | Si tiene casillas de ruta (`{businessId}`…), pega el `id` ahí. Si tiene un cuadro de texto negro, **borra todo y pega el JSON** |
| Enviar | Botón azul **Execute**. La respuesta sale abajo: fíjate en el **código** (201, 409…) y en el cuerpo |
| Iniciar sesión | Haz `POST /auth/login`, copia el valor de `token` (sin comillas), arriba clic en **Authorize**, pega **solo el token** y *Authorize*. **Si el candado de la operación está abierto, falta este paso y saldrá 401** |

## 3. El guion, escena por escena

> **Regla:** habla tranquilo, sin leer de corrido. Las frases **en cursiva** son lo que debes decir; si te sale con tus palabras, mejor, pero conserva las palabras clave en **negrita**.

### Escena 1 — Introducción (0:00–0:40)
**Qué haces:** muestra 8 segundos cada pestaña: el diagrama, GitHub Actions (todo en verde) y Sonar (Quality Gate OK).
**Qué dices:** *«Hola, soy [nombre], del equipo de Arquitectura. Les muestro el Sprint 2 de la **Plataforma de Reservas de Servicios**. Es un **monolito modular** desplegado en Render, con un pipeline de **GitHub Actions** que prueba, analiza con Sonar y despliega solo. Tiene **33 endpoints**, **658 pruebas automáticas** y **96 % de cobertura**. Vamos a probarlo todo desde Swagger.»*

### Escena 2 — Tour de Swagger (0:40–1:00) · pestaña PROVEEDOR
**Qué haces:** muestra los grupos (Autenticación, Usuarios, Proveedores, Catálogo, Servicios, Recursos, Reservas del cliente, Reservas del proveedor) y el botón Authorize.
**Qué dices:** *«Esta es la documentación interactiva. Tenemos **ocho grupos**; cada operación dice qué **historia de usuario** cubre y qué rol la puede usar. Los errores siempre vienen en un formato único con un identificador de trazabilidad.»*

### Escena 3 — Registro y acceso (1:00–1:50) · **HU-01, HU-02, HU-03**
| # | Qué haces | Pega / dónde | Debe salir |
|---|---|---|---|
| 1 | Pestaña PROVEEDOR: **Proveedores y negocio** → `POST /api/v1/providers` | `{"fullName":"Proveedor Demo","email":"demo.prov.<dia>@example.com","cellphone":"31011122<xx>","password":"<contraseña>","businessName":"Salón Demo"}` | **201.** Copia `businessId` y `userId` |
| 2 | Pestaña CLIENTE: **Usuarios** → `POST /api/v1/users` | `{"fullName":"Cliente Demo","email":"demo.cli.<dia>@example.com","cellphone":"31011133<xx>","password":"<contraseña>"}` | **201**, rol `CLIENTE`. Copia `id` |
| 3 | En las dos pestañas: **Autenticación y MFA** → `POST /api/v1/auth/login` y luego **Authorize** | `{"email":"<su correo>","password":"<contraseña>"}` | **200** con `token` |

**Qué dices:** *«Primero el **registro** (HU-01 y HU-03). Registro un **proveedor**, que crea su negocio, y un **cliente**. El rol lo decide el servidor, no el usuario. Luego **inicio de sesión** (HU-02): la API devuelve un **token JWT** de una hora, que pegamos en Authorize.»*
> `<dia>` = el día del mes (por ejemplo 20). `<xx>` = dos dígitos distintos en cada toma, para no repetir celular.

### Escena 4 — Configurar el negocio (1:50–3:00) · **HU-09, HU-14, HU-18, HU-19** · pestaña PROVEEDOR
| # | Operación | Pega | Debe salir |
|---|---|---|---|
| 4 | **Servicios** → `POST /businesses/{businessId}/services` | `{"name":"Corte de cabello","description":"Corte y peinado","durationMinutes":60,"priceCop":35000}` | **201.** Copia `id` (= serviceId) |
| 5 | **Recursos** → `POST /businesses/{businessId}/resources` | `{"name":"Sala 1","type":"SALA"}` | **201.** Copia `id` (= resourceId) |
| 6 | **Servicios** → `PUT /services/{serviceId}/resources` | `{"resourceIds":["<resourceId>"]}` | **200**, la sala asignada |
| 7 | **Recursos** → `PUT /resources/{resourceId}/availability` | `{"days":[{"dayOfWeek":1,"ranges":[{"start":"08:00","end":"20:00"}]},{"dayOfWeek":2,"ranges":[{"start":"08:00","end":"20:00"}]},{"dayOfWeek":3,"ranges":[{"start":"08:00","end":"20:00"}]},{"dayOfWeek":4,"ranges":[{"start":"08:00","end":"20:00"}]},{"dayOfWeek":5,"ranges":[{"start":"08:00","end":"20:00"}]}]}` | **200**, 7 días (sábado y domingo vacíos) |

**Qué dices:** *«Ahora el proveedor configura su negocio. **Crea un servicio** (HU-09) de 60 minutos y 35 mil pesos; **registra un recurso**, una sala (HU-14); **se lo asigna al servicio** (HU-18) y **define su horario** de lunes a viernes de 8 a 8 (HU-19). Cada operación valida que el negocio sea del proveedor que la hace.»*

### Escena 5 — El cliente reserva (3:00–3:50) · **HU-20, HU-22** · pestaña CLIENTE
| # | Operación | Pega | Debe salir |
|---|---|---|---|
| 8 | **Catálogo y disponibilidad** → `GET /services/{serviceId}/availability`, en `date` pon tu fecha | — | **200** con horarios cada 60 min |
| 9 | **Reservas del cliente** → `POST /api/v1/bookings` | `{"serviceId":"<serviceId>","date":"<fecha>","startTime":"10:00","endTime":"11:00"}` | **201**, `CONFIRMADA`. Copia `id` (= reserva 1) |
| 10 | **Ejecuta el mismo POST otra vez** | (igual) | **409:** «el horario ya tiene una reserva» |

**Qué dices:** *«El cliente consulta la **disponibilidad**, que es **pública**: no necesita iniciar sesión (HU-20). Reserva las 10 de la mañana (HU-22) y sale **confirmada**. Si intento reservar **el mismo horario otra vez**, el sistema responde **409**: es el **anti-overbooking**, que lo garantiza la **base de datos**, no solo la aplicación, y por eso funciona incluso si dos personas reservan a la vez.»*

### Escena 6 — Ver y cancelar (3:50–4:30) · **HU-24, HU-26, HU-23**
| # | Qué haces | Pega | Debe salir |
|---|---|---|---|
| 11 | PROVEEDOR: **Reservas del proveedor** → `GET /businesses/{businessId}/bookings` | — | **200**, con nombre y correo del cliente |
| 12 | PROVEEDOR: `POST /bookings/{reserva1}/provider-cancellation` | `{"reason":"El recurso está en mantenimiento"}` | **200**, `CANCELADA`, origen `PROVEEDOR` |
| 13 | CLIENTE: **Reservas del cliente** → `GET /bookings/me` | — | **200**, la reserva cancelada con el motivo |

**Qué dices:** *«El proveedor ve las **reservas de su negocio** con los datos del cliente (HU-24) y la **cancela con un motivo obligatorio** (HU-26). El cliente, en **mis reservas** (HU-23), ve que quedó cancelada y por qué.»*

### Escena 7 — Desactivar un recurso (4:30–5:30) · **HU-16, HU-17**
| # | Qué haces | Pega | Debe salir |
|---|---|---|---|
| 14 | CLIENTE: `POST /api/v1/bookings` | igual que el paso 9 pero `"startTime":"11:00","endTime":"12:00"` | **201** |
| 15 | PROVEEDOR: **Recursos** → `POST /resources/{resourceId}/deactivation` | `{"confirm":false}` | **409 `CONFIRMATION_REQUIRED`**, con la cantidad de reservas afectadas |
| 16 | La misma operación | `{"confirm":true}` | **200**, `active:false`, `cancelledBookings:1` |
| 17 | PROVEEDOR: `POST /resources/{resourceId}/reactivation` | (sin cuerpo) | **200**, `active:true` |

**Qué dices:** *«Hago otra reserva y ahora el proveedor quiere **desactivar la sala** (HU-16). Como tiene reservas futuras, el sistema **no lo hace** y pide **confirmación**, mostrando cuántas se cancelarían. Si confirmo, las cancela y desactiva la sala **en una sola transacción**: o pasa todo o no pasa nada. Y después puede **reactivarla** (HU-17).»*

### Escena 8 — Administrador y MFA (5:30–6:40) · **HU-05, HU-28**
| # | Qué haces | Pega | Debe salir |
|---|---|---|---|
| 18 | CLIENTE: `POST /api/v1/bookings` | igual que el 9 con `"startTime":"12:00","endTime":"13:00"` | **201** (reserva 3) |
| 19 | ADMIN: `POST /auth/login` **sin código** | `{"email":"<admin>","password":"<contraseña>"}` | **401 `MFA_REQUIRED`** |
| 20 | ADMIN: el mismo login **con** el código del celular | `{"email":"<admin>","password":"<contraseña>","mfaCode":"<6 dígitos>"}` | **200**. Copia el `token` → Authorize |
| 21 | ADMIN: **Usuarios** → `DELETE /users/{id del cliente}` y en el campo **X-MFA-Code** escribe el código vigente | — | **204** |
| 22 | PROVEEDOR: `GET /businesses/{businessId}/bookings` | — | La reserva 3 aparece `CANCELADA`, origen `ELIMINACION_CUENTA`, con el nombre del cliente aún visible |

**Qué dices:** *«Ahora el **administrador**. Si intenta entrar sin el segundo factor, la API responde **MFA requerido**; con el código de la app autenticadora, entra. Las operaciones sensibles, como **eliminar una cuenta**, piden el código otra vez (HU-05). Al eliminar al cliente (HU-28), sus **reservas futuras se cancelan automáticamente** y el **historial se conserva**: el proveedor sigue viendo la reserva con el nombre del cliente.»*
> El código de la app cambia cada 30 segundos y sirve por ~1 minuto. Si falla, **espera al siguiente código** y no insistas: 5 fallos seguidos bloquean 15 minutos.

### Escena 9 — Cierre (6:40–7:00)
**Qué dices:** *«Con esto vimos de punta a punta lo del Sprint 2: registro, negocio, reservas con **anti-overbooking**, cancelaciones, **recursos** y **administración con MFA**. Quedan para el Sprint 3 las historias de edición y mantenimiento, y completar la reserva. Gracias.»*

## 4. Después de grabar (1 minuto)

Con el token del administrador y un código vigente: `DELETE /users/{userId del proveedor}` (lo copiaste en el paso 1) para dejar la base limpia. El cliente ya se eliminó en el paso 21.

## 5. Si algo sale mal

| Qué ves | Qué hacer |
|---|---|
| **401** en un paso | Falta el token en el candado (**Authorize**), venció (1 hora) o es de otra cuenta. Haz login otra vez y pégalo |
| **403** | Estás con la pestaña equivocada (token de cliente en algo de proveedor, o al revés) |
| **429** | Demasiados registros o intentos fallidos: espera 10–15 minutos |
| **400** o **409** al reservar | La fecha es pasada o fin de semana, o la hora no está entre 08:00 y 20:00 |
| **404** «servicio no disponible» | Pegaste mal el `serviceId` |
| Tarda muchísimo la primera vez | Render estaba dormido; espera y recorta en la edición |

## 6. Para responder si preguntan (una línea cada una)

- **Monolito modular:** una sola aplicación desplegable dividida en módulos (identidad, proveedores, servicios, recursos, reservas) que se comunican por interfaces.
- **JWT:** el token de sesión; dura una hora y se puede revocar al cerrar sesión.
- **MFA:** segundo factor con códigos de 6 dígitos de una app autenticadora; es obligatorio para administradores.
- **409 / anti-overbooking:** la base de datos impide dos reservas confirmadas del mismo recurso en horarios que se traslapan.
- **HU-27 (completar reserva) y las de edición:** se dejaron para el Sprint 3; se priorizó el flujo completo de reserva.
