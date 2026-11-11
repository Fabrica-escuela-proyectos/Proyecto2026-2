package com.codefactory.reservas_backend.notification.application;

import com.codefactory.reservas_backend.common.config.TimeConfig;
import com.codefactory.reservas_backend.reservation.application.BookingsCancelled;
import com.codefactory.reservas_backend.reservation.application.BookingsCancelled.CancelledBooking;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * P5 de Calidad (2026-11-06): «se debe mandar correo» cuando al cliente le cancelan una reserva sin que él lo pida
 * (proveedor, servicio o recurso desactivado, cierre puntual, cuenta del proveedor eliminada). ADR-009.
 *
 * Escucha {@link BookingsCancelled} solo DESPUÉS de que la transacción confirma (si se deshace, no sale ningún
 * correo) y en otro hilo ({@code notificationExecutor}): un proveedor lento o caído no demora ni revierte la
 * cancelación. Un fallo se registra en el log (con el id de la reserva, no con el correo) y no se reintenta.
 */
@Component
@RequiredArgsConstructor
public class CancellationNotifier {

    private static final Logger log = LoggerFactory.getLogger(CancellationNotifier.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final EmailSender emailSender;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingsCancelled(BookingsCancelled event) {
        for (CancelledBooking booking : event.bookings()) {
            if (booking.clientEmail() == null || booking.clientEmail().isBlank()) {
                continue; // cliente eliminado: no hay a quién avisar
            }
            try {
                emailSender.send(compose(booking, event.origin(), event.reason()));
            } catch (RuntimeException e) {
                log.warn("No se pudo enviar el aviso de cancelación de la reserva {}: {}", booking.bookingId(), e.getMessage());
            }
        }
    }

    static EmailMessage compose(CancelledBooking b, String origin, String reason) {
        LocalDateTime start = LocalDateTime.ofInstant(b.startAt(), TimeConfig.BUSINESS_ZONE);
        String name = b.clientName() == null || b.clientName().isBlank() ? "" : " " + b.clientName();
        StringBuilder text = new StringBuilder()
                .append("Hola").append(name).append(":\n\n")
                .append("Tu reserva de ").append(b.serviceName()).append(" en ").append(b.businessName())
                .append(" para el ").append(start.format(DATE)).append(" a las ").append(start.format(TIME))
                .append(" (hora de Colombia) fue cancelada porque ").append(cause(origin)).append(".\n");
        if (reason != null && !reason.isBlank()) {
            text.append("Motivo: ").append(reason.trim()).append("\n");
        }
        text.append("\nPuedes consultar tus reservas y elegir otro horario en la plataforma.\n\n")
                .append("Este es un mensaje automático; por favor no lo respondas.\n");
        return new EmailMessage(b.clientEmail(), b.clientName(), "Tu reserva en " + b.businessName() + " fue cancelada",
                text.toString());
    }

    /** Origen de la cancelación (nombre de {@code CancelOrigin}) en palabras para el cliente. */
    private static String cause(String origin) {
        return switch (origin == null ? "" : origin) {
            case "SERVICIO_NO_DISPONIBLE" -> "el servicio dejó de estar disponible";
            case "RECURSO_NO_DISPONIBLE" -> "el recurso asignado no estará disponible en ese horario";
            case "ELIMINACION_CUENTA" -> "el negocio ya no está en la plataforma";
            case "CLIENTE" -> "la cancelaste tú";
            default -> "el negocio la canceló";
        };
    }
}
