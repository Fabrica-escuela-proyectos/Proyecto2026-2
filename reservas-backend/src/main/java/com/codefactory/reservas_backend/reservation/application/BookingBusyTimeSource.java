package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.common.config.TimeConfig;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.service.application.BusyTimeSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Enlaza las reservas con el motor de disponibilidad (HU-20): las reservas
 * CONFIRMADAS de un recurso son tiempo ocupado y dejan de mostrarse como libres.
 */
@Component
@RequiredArgsConstructor
public class BookingBusyTimeSource implements BusyTimeSource {

    private static final ZoneId ZONE = TimeConfig.BUSINESS_ZONE;

    private final BookingRepository bookingRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BusyInterval> busyOn(Collection<UUID> resourceIds, LocalDate date) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        return bookingRepository.findConfirmedTouching(resourceIds,
                        date.atStartOfDay(ZONE).toInstant(), date.plusDays(1).atStartOfDay(ZONE).toInstant()).stream()
                .map(b -> new BusyInterval(b.getResourceId(),
                        b.getStartAt().atZone(ZONE).toLocalDateTime(), b.getEndAt().atZone(ZONE).toLocalDateTime()))
                .toList();
    }
}
