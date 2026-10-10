package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingItem;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingNotCancellableException;
import com.codefactory.reservas_backend.reservation.domain.BookingNotFoundException;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingCancellationServiceImpl implements BookingCancellationService {

    static final String NOT_FOUND_MESSAGE = "La reserva solicitada no existe";
    static final String ALREADY_CANCELLED_MESSAGE = "La reserva ya está cancelada";
    static final String COMPLETED_MESSAGE = "La reserva ya fue completada y no se puede cancelar";
    static final String TOO_LATE_MESSAGE = "Solo se puede cancelar una reserva con al menos 1 hora de antelación a su inicio";

    private final BookingRepository bookingRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional
    public BookingItem cancelAsClient(UUID bookingId, String reason, UserIdentity client, String originIp) {
        // Bloqueo de fila: dos cancelaciones simultáneas de la misma reserva se ejecutan una tras otra
        // y la segunda ve el estado ya cancelado (409), en vez de cancelar dos veces.
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(NOT_FOUND_MESSAGE));

        if (booking.getClientId() == null || !booking.getClientId().equals(client.id())) {
            throw new AccessDeniedException("Un cliente solo puede cancelar sus propias reservas");
        }
        if (booking.getStatus() == BookingStatus.CANCELADA) {
            throw new BookingNotCancellableException(ALREADY_CANCELLED_MESSAGE);
        }
        if (booking.getStatus() == BookingStatus.COMPLETADA) {
            throw new BookingNotCancellableException(COMPLETED_MESSAGE);
        }
        Instant now = clock.instant();
        if (booking.getStartAt().isBefore(now.plusSeconds(MIN_NOTICE_HOURS * 3600L))) {
            throw new BookingNotCancellableException(TOO_LATE_MESSAGE);
        }

        booking.setStatus(BookingStatus.CANCELADA);
        booking.setCancelOrigin(CancelOrigin.CLIENTE);
        booking.setCancelReason(reason == null || reason.isBlank() ? null : reason.trim());
        booking.setCancelledAt(now);
        bookingRepository.saveAndFlush(booking);

        // Notificaciones están fuera de alcance (plan, decisión 5): el aviso al proveedor queda
        // como evento de auditoría con el negocio, servicio y recurso afectados.
        auditService.registerEvent(AuditEventType.CANCELACION_RESERVA, client.email(), "SUCCESS",
                "Reserva " + booking.getId() + " cancelada por el cliente (negocio " + booking.getBusinessId()
                        + ", servicio " + booking.getServiceId() + ", recurso " + booking.getResourceId() + ")", originIp);
        return BookingMapper.toItem(booking);
    }
}
