package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingBulkCancellationServiceImpl implements BookingBulkCancellationService {

    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public long countFutureOfResource(UUID resourceId) {
        return bookingRepository.countFutureConfirmedOfResource(resourceId, clock.instant());
    }

    @Override
    @Transactional
    public int cancelFutureOfClient(UUID clientId, CancelOrigin origin, String reason) {
        // Sin aviso: es la cuenta del propio cliente la que se está eliminando (HU-28).
        return bookingRepository.cancelFutureConfirmedOfClient(clientId, origin, reason, clock.instant());
    }

    @Override
    @Transactional
    public int cancelFutureOfBusinesses(Collection<UUID> businessIds, CancelOrigin origin, String reason) {
        if (businessIds.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        List<Booking> affected = bookingRepository.findFutureConfirmedOfBusinesses(businessIds, now);
        int cancelled = bookingRepository.cancelFutureConfirmedOfBusinesses(businessIds, origin, reason, now);
        announce(origin, reason, affected);
        return cancelled;
    }

    @Override
    @Transactional
    public int cancelFutureOfResource(UUID resourceId, CancelOrigin origin, String reason) {
        Instant now = clock.instant();
        List<Booking> affected = bookingRepository.findFutureConfirmedOfResource(resourceId, now);
        int cancelled = bookingRepository.cancelFutureConfirmedOfResource(resourceId, origin, reason, now);
        announce(origin, reason, affected);
        return cancelled;
    }

    @Override
    @Transactional(readOnly = true)
    public long countFutureOfService(UUID serviceId) {
        return bookingRepository.countFutureConfirmedOfService(serviceId, clock.instant());
    }

    @Override
    @Transactional
    public int cancelFutureOfService(UUID serviceId, CancelOrigin origin, String reason) {
        Instant now = clock.instant();
        List<Booking> affected = bookingRepository.findFutureConfirmedOfService(serviceId, now);
        int cancelled = bookingRepository.cancelFutureConfirmedOfService(serviceId, origin, reason, now);
        announce(origin, reason, affected);
        return cancelled;
    }

    @Override
    @Transactional(readOnly = true)
    public long countFutureOfResourceIn(UUID resourceId, Instant start, Instant end) {
        return bookingRepository.countFutureConfirmedOfResourceIn(resourceId, start, end, clock.instant());
    }

    @Override
    @Transactional
    public int cancelFutureOfResourceIn(UUID resourceId, Instant start, Instant end, CancelOrigin origin, String reason) {
        Instant now = clock.instant();
        List<Booking> affected = bookingRepository.findFutureConfirmedOfResourceIn(resourceId, start, end, now);
        int cancelled = bookingRepository.cancelFutureConfirmedOfResourceIn(resourceId, start, end, origin, reason, now);
        announce(origin, reason, affected);
        return cancelled;
    }

    @Override
    @Transactional
    public int anonymizeClient(UUID clientId) {
        return bookingRepository.anonymizeClient(clientId, DELETED_CLIENT_NAME);
    }

    /**
     * Publica qué reservas se cancelaron (se leyeron con los mismos filtros, en la misma transacción y con el
     * recurso o servicio ya bloqueado). El correo sale solo si la transacción confirma (ADR-009).
     */
    private void announce(CancelOrigin origin, String reason, List<Booking> affected) {
        if (!affected.isEmpty()) {
            eventPublisher.publishEvent(BookingsCancelled.of(origin, reason, affected));
        }
    }
}
