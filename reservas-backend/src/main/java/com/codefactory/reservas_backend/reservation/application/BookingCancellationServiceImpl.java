package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
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
    static final String ALREADY_STARTED_MESSAGE = "La reserva ya inició o finalizó y no se puede cancelar";

    private final BookingRepository bookingRepository;
    private final BusinessAccessService businessAccessService;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional
    public BookingItem cancelAsClient(UUID bookingId, String reason, UserIdentity client, String originIp) {
        Booking booking = lockBooking(bookingId);

        if (booking.getClientId() == null || !booking.getClientId().equals(client.id())) {
            throw new AccessDeniedException("Un cliente solo puede cancelar sus propias reservas");
        }
        requireConfirmed(booking);
        Instant now = clock.instant();
        if (booking.getStartAt().isBefore(now.plusSeconds(MIN_NOTICE_HOURS * 3600L))) {
            throw new BookingNotCancellableException(TOO_LATE_MESSAGE);
        }

        applyCancellation(booking, CancelOrigin.CLIENTE, reason, now);
        // Notificaciones están fuera de alcance (plan, decisión 5): el aviso al proveedor queda
        // como evento de auditoría con el negocio, servicio y recurso afectados.
        auditService.registerEvent(AuditEventType.CANCELACION_RESERVA, client.email(), "SUCCESS",
                "Reserva " + booking.getId() + " cancelada por el cliente (negocio " + booking.getBusinessId()
                        + ", servicio " + booking.getServiceId() + ", recurso " + booking.getResourceId() + ")", originIp);
        return BookingMapper.toItem(booking);
    }

    @Override
    @Transactional
    public BookingItem cancelAsProvider(UUID bookingId, String reason, UserIdentity provider, String originIp) {
        Booking booking = lockBooking(bookingId);

        // Una reserva cuyo negocio ya no existe (business_id nulo) no es de ningún proveedor.
        if (booking.getBusinessId() == null) {
            throw new AccessDeniedException("La reserva no pertenece a un negocio de este proveedor");
        }
        businessAccessService.requireOwner(booking.getBusinessId(), provider);
        requireConfirmed(booking);
        Instant now = clock.instant();
        // Sin regla de antelación para el proveedor (plan, decisión 12), pero no tiene sentido cancelar
        // una reserva que ya empezó: HU-16/HU-28 también solo cancelan reservas futuras.
        if (!booking.getStartAt().isAfter(now)) {
            throw new BookingNotCancellableException(ALREADY_STARTED_MESSAGE);
        }

        applyCancellation(booking, CancelOrigin.PROVEEDOR, reason, now);
        // El aviso al cliente (fuera de alcance) queda como evento de auditoría con su correo.
        auditService.registerEvent(AuditEventType.CANCELACION_RESERVA, provider.email(), "SUCCESS",
                "Reserva " + booking.getId() + " cancelada por el proveedor (aviso al cliente "
                        + booking.getClientEmail() + ", servicio " + booking.getServiceId()
                        + ", recurso " + booking.getResourceId() + ")", originIp);
        return BookingMapper.toItem(booking);
    }

    /**
     * Bloqueo de fila: dos cancelaciones simultáneas de la misma reserva se ejecutan una tras otra y
     * la segunda ve el estado ya cancelado (409), en vez de cancelar dos veces.
     */
    private Booking lockBooking(UUID bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(NOT_FOUND_MESSAGE));
    }

    private static void requireConfirmed(Booking booking) {
        if (booking.getStatus() == BookingStatus.CANCELADA) {
            throw new BookingNotCancellableException(ALREADY_CANCELLED_MESSAGE);
        }
        if (booking.getStatus() == BookingStatus.COMPLETADA) {
            throw new BookingNotCancellableException(COMPLETED_MESSAGE);
        }
    }

    private void applyCancellation(Booking booking, CancelOrigin origin, String reason, Instant now) {
        booking.setStatus(BookingStatus.CANCELADA);
        booking.setCancelOrigin(origin);
        booking.setCancelReason(reason == null || reason.isBlank() ? null : reason.trim());
        booking.setCancelledAt(now);
        bookingRepository.saveAndFlush(booking);
    }
}
