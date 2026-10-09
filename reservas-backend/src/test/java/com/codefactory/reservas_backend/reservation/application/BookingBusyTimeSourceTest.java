package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.service.application.BusyTimeSource.BusyInterval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingBusyTimeSourceTest {

    @Mock
    private BookingRepository repository;

    private BookingBusyTimeSource source;

    @BeforeEach
    void setUp() {
        source = new BookingBusyTimeSource(repository);
    }

    @Test
    void debeConsultarElDiaCompletoEnHoraDeBogotaYDevolverLosIntervalosEnHoraLocal() {
        UUID resource = UUID.randomUUID();
        Set<UUID> ids = Set.of(resource);
        LocalDate date = LocalDate.of(2026, 10, 19);
        // El día 19 en Bogotá (UTC-5) va de las 05:00Z del 19 a las 05:00Z del 20.
        Instant dayStart = Instant.parse("2026-10-19T05:00:00Z");
        Instant dayEnd = Instant.parse("2026-10-20T05:00:00Z");
        when(repository.findConfirmedTouching(ids, dayStart, dayEnd)).thenReturn(List.of(Booking.builder()
                .resourceId(resource)
                .startAt(Instant.parse("2026-10-19T15:00:00Z")).endAt(Instant.parse("2026-10-19T16:00:00Z")).build()));

        List<BusyInterval> busy = source.busyOn(ids, date);

        assertThat(busy).containsExactly(new BusyInterval(resource,
                LocalDateTime.of(2026, 10, 19, 10, 0), LocalDateTime.of(2026, 10, 19, 11, 0)));
    }

    @Test
    void sinRecursosNoConsultaLaBase() {
        assertThat(source.busyOn(Set.of(), LocalDate.of(2026, 10, 19))).isEmpty();
        verify(repository, never()).findConfirmedTouching(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
