package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingBulkCancellationServiceImpl implements BookingBulkCancellationService {

    private final BookingRepository bookingRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public long countFutureOfResource(UUID resourceId) {
        return bookingRepository.countFutureConfirmedOfResource(resourceId, clock.instant());
    }

    @Override
    @Transactional
    public int cancelFutureOfClient(UUID clientId, CancelOrigin origin, String reason) {
        return bookingRepository.cancelFutureConfirmedOfClient(clientId, origin, reason, clock.instant());
    }

    @Override
    @Transactional
    public int cancelFutureOfBusinesses(Collection<UUID> businessIds, CancelOrigin origin, String reason) {
        if (businessIds.isEmpty()) {
            return 0;
        }
        return bookingRepository.cancelFutureConfirmedOfBusinesses(businessIds, origin, reason, clock.instant());
    }

    @Override
    @Transactional
    public int cancelFutureOfResource(UUID resourceId, CancelOrigin origin, String reason) {
        return bookingRepository.cancelFutureConfirmedOfResource(resourceId, origin, reason, clock.instant());
    }
}
