package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingItem;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStateConflictException;
import com.codefactory.reservas_backend.reservation.domain.BookingNotFoundException;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingCompletionServiceImpl implements BookingCompletionService {

    static final String NOT_FOUND_MESSAGE = "La reserva solicitada no existe";
    static final String CANCELLED_MESSAGE = "La reserva está cancelada y no se puede completar";
    static final String ALREADY_COMPLETED_MESSAGE = "La reserva ya está completada";
    static final String NOT_FINISHED_MESSAGE = "La reserva solo se puede completar cuando haya terminado su horario";

    private final BookingRepository bookingRepository;
    private final BusinessAccessService businessAccessService;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional
    public BookingItem complete(UUID bookingId, UserIdentity provider, String originIp) {
        // Bloqueo de fila: completar y cancelar a la vez la misma reserva se ejecutan una tras otra.
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(NOT_FOUND_MESSAGE));

        if (booking.getBusinessId() == null) {
            throw new AccessDeniedException("La reserva no pertenece a un negocio de este proveedor");
        }
        businessAccessService.requireOwner(booking.getBusinessId(), provider);

        if (booking.getStatus() == BookingStatus.CANCELADA) {
            throw new BookingStateConflictException(CANCELLED_MESSAGE);
        }
        if (booking.getStatus() == BookingStatus.COMPLETADA) {
            throw new BookingStateConflictException(ALREADY_COMPLETED_MESSAGE);
        }
        if (booking.getEndAt().isAfter(clock.instant())) {
            throw new BookingStateConflictException(NOT_FINISHED_MESSAGE);
        }

        booking.setStatus(BookingStatus.COMPLETADA);
        bookingRepository.saveAndFlush(booking);
        auditService.registerEvent(AuditEventType.FINALIZACION_RESERVA, provider.email(), "SUCCESS",
                "Reserva " + booking.getId() + " completada (negocio " + booking.getBusinessId()
                        + ", servicio " + booking.getServiceId() + ")", originIp);
        return BookingMapper.toItem(booking);
    }
}
