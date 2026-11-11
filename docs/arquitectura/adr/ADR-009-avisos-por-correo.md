# ADR-009 — Avisos por correo al cliente cuando le cancelan una reserva

* **Estado:** Propuesto. Implementado el 2026-11-11; pendiente de revisión del equipo.
* **Fecha:** 2026-11-11
* **Responsable:** Simon Betancur.
* **Decisión:** un módulo nuevo `notification` escucha el evento de dominio `BookingsCancelled`, **después de que la transacción confirma y en otro hilo**, y envía un correo de texto plano a cada cliente afectado por la **API HTTPS de Brevo**. Sin clave de Brevo, el correo solo se escribe en el log.

## 1. Contexto

Hasta el Sprint 3, el «aviso al cliente» de las cancelaciones era un evento de auditoría (supuestos S-02 y D12). El 2026-11-06 Calidad respondió a la pregunta P5 «**se debe mandar correo**». Además, **Render gratuito no permite salir por los puertos 25, 465 y 587** (render.com/docs/free, verificado el 2026-11-11), así que el correo no puede ir por SMTP.

## 2. Alternativas

| Alternativa | Por qué no |
|---|---|
| Enviar el correo dentro del servicio que cancela, en la misma transacción | Un proveedor lento demoraría la respuesta y uno caído **desharía la cancelación**; además un correo enviado no se puede deshacer si después la transacción falla |
| SMTP con Spring Mail | Render gratuito bloquea los puertos SMTP |
| Cola de mensajes (RabbitMQ, tabla *outbox*) con reintentos | Más infraestructura de la que el alcance y el plan gratuito justifican; queda como evolución |
| **Evento después de confirmar + envío asíncrono por API HTTPS (elegida)** | Simple, no afecta la cancelación y funciona en Render |

## 3. Decisión

| # | Decisión |
|---|---|
| D1 | `reservation` publica `BookingsCancelled` (origen como texto, motivo y copias de cada reserva: correo, nombre, negocio, servicio, recurso, inicio) en **todas las cancelaciones que no hace el propio cliente**: HU-26 (proveedor), HU-11 (servicio desactivado), HU-16 (recurso desactivado), HU-21 (cierre) y HU-28 (cuenta del proveedor eliminada). `reservation` **no conoce** a `notification` (como el evento de HU-28, ADR-007). |
| D2 | `notification.application.CancellationNotifier` escucha con `@TransactionalEventListener(AFTER_COMMIT)` y `@Async("notificationExecutor")` (1–2 hilos, cola de 500): si la transacción se deshace no sale ningún correo, y el envío nunca demora ni revierte la cancelación. Un fallo se registra en el log con el id de la reserva y **no se reintenta**. |
| D3 | Puerto `EmailSender` con dos implementaciones: `BrevoEmailSender` (`POST https://api.brevo.com/v3/smtp/email`, cabecera `api-key`, tiempo límite de 10 s) cuando existen `BREVO_API_KEY` y `NOTIFICATION_SENDER_EMAIL`; `LogEmailSender` en otro caso (desarrollo, pruebas, despliegue sin configurar). |
| D4 | **Texto plano**, sin HTML: no hay nada que escapar ni inyectar. Fecha y hora en hora de Colombia. |
| D5 | **No se envía a dominios reservados para pruebas** (`example.com/.org/.net`, `.test`, `.example`, `.invalid`, `localhost`): las corridas de Postman y las pruebas de Calidad contra Render no gastan el cupo diario de Brevo (300 en el plan gratuito) ni generan rebotes. |
| D6 | Seguridad: la clave solo en variables de entorno (nunca en el repositorio, el log ni las excepciones); URL fija, sin datos del usuario (sin SSRF, OWASP A10); el log enmascara el destinatario (`a***@dominio`). |
| D7 | Si el cliente ya no existe (cuenta eliminada, P12) la reserva no tiene correo y no se envía nada. |

## 4. Consecuencias

* **Positivas:** cumple P5 sin tocar las reglas de cancelación; el módulo se puede quitar o cambiar de proveedor sin tocar `reservation`; probado con un servidor HTTP falso (formato del pedido a Brevo, errores, dominios reservados) y de punta a punta (`CancellationEmailIntegrationTest`).
* **Negativas / límites:** sin reintentos ni *outbox*, un correo que falle (proveedor caído, cupo agotado) se pierde, aunque la cancelación sigue siendo válida y queda en la auditoría; si el proceso se reinicia con correos en cola, también se pierden. Depende de una cuenta externa (Brevo) y de un remitente verificado. No avisa al proveedor cuando el cliente cancela (HU-25): no se pidió.
* **No verificado en este repositorio:** el envío real a Brevo (requiere la clave del equipo). Ver la guía de despliegue §4.3 para configurarlo y probarlo.
