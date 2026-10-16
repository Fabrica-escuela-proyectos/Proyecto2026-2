# Guía del video de demostración — Sprint 2 (7 minutos, con Swagger)

Demuestra de punta a punta lo que se hizo en el Sprint 2 usando solo **Swagger UI** sobre la aplicación desplegada: `https://proyecto2026-2-5zoo.onrender.com/swagger-ui.html`. Los datos de la demo se crean en vivo y se limpian al final. **No escribas ni muestres contraseñas reales en el video** (las pones tú; la guía usa marcadores).

## 0. Antes de grabar (10 minutos de preparación)

| # | Qué hacer | Por qué |
|---|---|---|
| 1 | **Despierta Render 5 minutos antes:** abre `https://proyecto2026-2-5zoo.onrender.com/actuator/health` hasta ver `{"status":"UP"}` | Se duerme a los 15 min y el primer request tarda ≈ 2 min |
| 2 | Abre **3 pestañas de Swagger**: **A = proveedor**, **B = cliente**, **C = administrador** | Cada pestaña guarda su propio *Authorize*; así no cierras y abres sesión en cámara |
| 3 | Deja en otras pestañas, para la intro: el [diagrama de despliegue](https://github.com/Fabrica-escuela-proyectos/Proyecto2026-2/blob/main/docs/arquitectura/diagramas/diagrama-despliegue.png), [GitHub Actions](https://github.com/Fabrica-escuela-proyectos/Proyecto2026-2/actions) y [SonarCloud](https://sonarcloud.io/project/overview?id=Fabrica-escuela-proyectos_Proyecto2026-2) | 3 imágenes de 8 s cada una |
| 4 | Ten un **bloc de notas** con los cuerpos JSON de la sección 2 ya editados (correos y celulares únicos; fecha) | Escribir en vivo se come el tiempo |
| 5 | **Fecha de la reserva:** el **próximo día hábil** (lunes a viernes) con al menos 1 día de margen. El horario que cargamos es L–V 08:00–20:00 | Una fecha pasada o fin de semana da error |
| 6 | **Contraseña de demo** que cumpla la política (≥ 8 caracteres, mayúscula, minúscula y símbolo) | La pones tú; no queda escrita en la guía |
| 7 | Administrador demo: ten **abierta la app autenticadora** y las credenciales a mano | El código TOTP dura 30 s |
| 8 | Zoom del navegador al 110–125 %, y graba a 1080p | Que se lean los códigos y los JSON |

**Límites que pueden arruinar una toma:**
- **Registro: 5 por IP cada 10 minutos.** El video usa **2** (proveedor y cliente). Puedes hacer **2 tomas en 10 minutos**; si repites más, espera.
- **Login y MFA: 5 fallos seguidos bloquean 15 minutos.** No reintentes a ciegas un código TOTP: espera al siguiente.
- El token dura 1 hora: no hay que renovarlo durante la grabación.
- En *Authorize* se pega **solo el token** (sin la palabra `Bearer`).

## 1. Cronograma (7:00)

| Tiempo | Segmento | Qué hacer | Qué decir (una frase) |
|---|---|---|---|
| 0:00–0:40 | **Intro** | Mostrar 8 s cada una: diagrama de despliegue, pipeline verde, Sonar (Quality Gate OK) | «Monolito modular en Render con CI/CD en GitHub Actions y Sonar; 33 endpoints, 658 pruebas, 96 % de cobertura.» |
| 0:40–1:00 | **Tour de Swagger** | Pestaña A: mostrar los **8 grupos** y el candado de *Authorize* | «Cada operación indica su HU y su rol; los errores usan un formato único con `traceId`.» |
| 1:00–1:50 | **Registro y acceso** (HU-01, 02, 03) | `POST /providers` → copiar `businessId` · `POST /users` (pestaña B) · `POST /auth/login` en A y en B → *Authorize* con cada token | «El rol lo asigna el servidor; el login devuelve un JWT de una hora.» |
| 1:50–3:00 | **Configurar el negocio** (HU-09, 14, 18, 19) | En A: `POST …/services` → copiar `id` · `POST …/resources` → copiar `id` · `PUT /services/{id}/resources` · `PUT /resources/{id}/availability` | «Servicio de 60 minutos, una sala, asignada al servicio, con horario de lunes a viernes.» |
| 3:00–3:50 | **El cliente reserva** (HU-20, 22, anti-overbooking) | En B: `GET /services/{id}/availability` (**sin candado: es pública**) → `POST /bookings` 10:00 → **repetirlo: 409** | «La disponibilidad es pública; el anti-overbooking lo garantiza la base de datos, no la aplicación.» |
| 3:50–4:30 | **Ver y cancelar** (HU-24, 26, 23) | En A: `GET …/businesses/{id}/bookings` (ver nombre y correo del cliente) → `POST /bookings/{id}/provider-cancellation` con motivo → en B: `GET /bookings/me` (ve el motivo) | «El proveedor cancela con motivo obligatorio y el cliente lo ve.» |
| 4:30–5:30 | **Desactivar un recurso** (HU-16, 17) | En B: nueva reserva 11:00 · en A: `POST /resources/{id}/deactivation` con `{"confirm": false}` → **409 `CONFIRMATION_REQUIRED`** → con `true` → `cancelledBookings: 1` → `…/reactivation` | «Pide confirmación antes de cancelar reservas; todo ocurre en una transacción.» |
| 5:30–6:40 | **Administrador y MFA** (HU-05, 28) | En B: reserva 12:00 · en C: login **sin código → 401 `MFA_REQUIRED`**, luego con código → `DELETE /users/{clienteId}` con `X-MFA-Code` → en A: `GET …/bookings` muestra la reserva `CANCELADA` por `ELIMINACION_CUENTA` con el nombre del cliente conservado | «Las operaciones sensibles exigen un segundo factor; al eliminar la cuenta se cancelan sus reservas futuras y se conserva el historial.» |
| 6:40–7:00 | **Cierre** | Volver a Sonar/Actions o a la pantalla de Swagger | «Quedan para el Sprint 3 las HU de edición y mantenimiento y completar reserva.» |

> **Si vas pasado de tiempo, recorta en este orden:** (1) HU-17 reactivar; (2) la intro de Sonar/Actions; (3) el segundo registro mostrando solo el resultado. **Extras si sobran 10–20 s:** `GET /businesses` (HU-13), `GET/PUT …/booking-lead-time` (HU-08), `POST /bookings/{id}/cancellation` como cliente (HU-25), o un 403 al llamar con el token del cliente a un endpoint de proveedor (HU-06).
> **Ensaya al menos una vez:** con los cuerpos pegados, cada paso lleva 8–12 s; escribiendo en vivo tarda el triple.

## 2. Cuerpos y respuestas esperadas (para copiar y pegar)

Sustituye `<…>`. Los celulares son de 10 dígitos que empiezan por 3 y **no deben repetirse** entre tomas.

| Paso | Operación (Swagger) | Cuerpo JSON | Respuesta esperada / qué copiar |
|---|---|---|---|
| 1 | **Proveedores y negocio** · `POST /api/v1/providers` | `{"fullName":"Proveedor Demo","email":"demo.prov.<dia>@example.com","cellphone":"31011122<xx>","password":"<contraseña>","businessName":"Salón Demo"}` | `201` · copiar **`businessId`** |
| 2 | **Usuarios** · `POST /api/v1/users` (pestaña B) | `{"fullName":"Cliente Demo","email":"demo.cli.<dia>@example.com","cellphone":"31011133<xx>","password":"<contraseña>"}` | `201`, `role: CLIENTE` |
| 3 | **Autenticación** · `POST /api/v1/auth/login` | `{"email":"<correo>","password":"<contraseña>"}` (uno por pestaña) | `200` · copiar **`token`** → *Authorize* |
| 4 | **Servicios** · `POST /businesses/{businessId}/services` | `{"name":"Corte de cabello","description":"Corte y peinado","durationMinutes":60,"priceCop":35000}` | `201` · copiar **`id`** (serviceId) |
| 5 | **Recursos** · `POST /businesses/{businessId}/resources` | `{"name":"Sala 1","type":"SALA"}` | `201` · copiar **`id`** (resourceId) |
| 6 | **Servicios** · `PUT /services/{serviceId}/resources` | `{"resourceIds":["<resourceId>"]}` | `200`, lista con la sala |
| 7 | **Recursos** · `PUT /resources/{resourceId}/availability` | `{"days":[{"dayOfWeek":1,"ranges":[{"start":"08:00","end":"20:00"}]},{"dayOfWeek":2,"ranges":[{"start":"08:00","end":"20:00"}]},{"dayOfWeek":3,"ranges":[{"start":"08:00","end":"20:00"}]},{"dayOfWeek":4,"ranges":[{"start":"08:00","end":"20:00"}]},{"dayOfWeek":5,"ranges":[{"start":"08:00","end":"20:00"}]}]}` | `200`, 7 días (fin de semana vacío) |
| 8 | **Catálogo y disponibilidad** · `GET /services/{serviceId}/availability?date=<AAAA-MM-DD>` | — | `200` con horarios libres cada 60 min; **sin token** |
| 9 | **Reservas del cliente** · `POST /api/v1/bookings` | `{"serviceId":"<serviceId>","date":"<AAAA-MM-DD>","startTime":"10:00","endTime":"11:00"}` | `201` `CONFIRMADA` · copiar **`id`** (bookingId1) |
| 10 | La misma petición otra vez | (igual) | **`409`** «El horario seleccionado no está disponible: ya tiene una reserva» |
| 11 | **Reservas del proveedor** · `GET /businesses/{businessId}/bookings` | — | `200`, con `clientName` y `clientEmail` |
| 12 | `POST /bookings/{bookingId1}/provider-cancellation` | `{"reason":"El recurso está en mantenimiento"}` | `200`, `CANCELADA`, origen `PROVEEDOR` |
| 13 | **Reservas del cliente** · `GET /bookings/me` | — | La reserva `CANCELADA` con el motivo |
| 14 | `POST /bookings` (cliente) | igual que el 9 con `"startTime":"11:00","endTime":"12:00"` | `201` (bookingId2) |
| 15 | **Recursos** · `POST /resources/{resourceId}/deactivation` | `{"confirm":false}` | **`409` `CONFIRMATION_REQUIRED`**, `fields.affectedBookings: "1"` |
| 16 | La misma operación | `{"confirm":true}` | `200`, `active:false`, `cancelledBookings:1` |
| 17 | `POST /resources/{resourceId}/reactivation` | — | `200`, `active:true` |
| 18 | `POST /bookings` (cliente) | igual que el 9 con `"startTime":"12:00","endTime":"13:00"` | `201` (bookingId3) |
| 19 | **Autenticación** · `POST /auth/login` (pestaña C, admin) | `{"email":"<admin>","password":"<contraseña>"}` | **`401` `MFA_REQUIRED`** |
| 20 | Igual **con** el código de la app | `{"email":"<admin>","password":"<contraseña>","mfaCode":"<6 dígitos>"}` | `200` · copiar `token` → *Authorize* en C |
| 21 | **Usuarios** · `DELETE /users/{clienteId}` | Header **`X-MFA-Code`** = código vigente (puede ser el mismo si no pasaron 30 s; si no, el nuevo) | `204` |
| 22 | En A: `GET /businesses/{businessId}/bookings` | — | bookingId3 **`CANCELADA`**, `cancelOrigin: ELIMINACION_CUENTA`, `clientName` y `clientEmail` conservados |

> **El id del cliente** (para el paso 21) sale en la respuesta del paso 2 (`id`).
> Los ids se pegan en el campo del parámetro de ruta de cada operación; deja todos en el bloc de notas conforme aparezcan.

## 3. Limpieza al terminar (fuera de cámara, 1 minuto)

Con el token del administrador (pestaña C) y un código vigente: `DELETE /users/{userId del proveedor}` (el `userId` está en la respuesta del paso 1). Así la base de Render queda sin los datos de la demo. El cliente ya se eliminó en el paso 21.

## 4. Qué HU cubre el video

| HU | Dónde |
|---|---|
| 01, 02, 03 | Pasos 1–3 |
| 09, 14, 18, 19 | Pasos 4–7 |
| 20 (pública) | Paso 8 |
| 22 + anti-overbooking | Pasos 9–10 |
| 24 | Paso 11 |
| 26 | Paso 12 |
| 23 | Paso 13 |
| 16, 17 | Pasos 15–17 |
| 05, 28 y MFA | Pasos 19–22 |
| Opcionales | 13, 08, 25 y 06 (ver extras) |

## 5. Si algo falla en cámara

| Síntoma | Causa y arreglo |
|---|---|
| `401` en cualquier paso protegido | Falta *Authorize* en esa pestaña o el token es de otro rol; vuelve a pegar el token correcto (sin `Bearer`) |
| `429` | Demasiados registros o fallos de login/MFA: espera 10–15 minutos |
| `400` «La fecha…» o `409` «fuera del horario» | La fecha es pasada o fin de semana, o la hora no cae dentro de 08:00–20:00 |
| `403` en una operación de proveedor | Estás con el token del cliente (o viceversa) |
| `404` «El servicio no está disponible» | El `serviceId` está mal pegado o es de otra toma |
| La primera petición tarda mucho | Render estaba dormido; espera y recorta en edición |
