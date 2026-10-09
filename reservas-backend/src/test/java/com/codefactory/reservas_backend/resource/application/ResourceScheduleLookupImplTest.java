package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.resource.domain.ResourceAvailability;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceAvailabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceScheduleLookupImplTest {

    @Mock
    private ResourceAvailabilityRepository repository;

    private ResourceScheduleLookupImpl lookup;

    @BeforeEach
    void setUp() {
        lookup = new ResourceScheduleLookupImpl(repository);
    }

    private static ResourceAvailability row(UUID resourceId, int day, int startHour, int endHour) {
        return ResourceAvailability.builder().resourceId(resourceId).dayOfWeek(day)
                .startTime(LocalTime.of(startHour, 0)).endTime(LocalTime.of(endHour, 0)).build();
    }

    @Test
    void debeAgruparPorRecursoYOrdenarLosRangosPorHoraDeInicio() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Set<UUID> ids = Set.of(a, b);
        when(repository.findByResourceIdInAndDayOfWeek(ids, 1)).thenReturn(List.of(
                row(a, 1, 14, 18), row(b, 1, 9, 10), row(a, 1, 9, 12)));

        Map<UUID, List<TimeWindow>> result = lookup.windowsOn(ids, DayOfWeek.MONDAY);

        assertThat(result.get(a)).containsExactly(
                new TimeWindow(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                new TimeWindow(LocalTime.of(14, 0), LocalTime.of(18, 0)));
        assertThat(result.get(b)).containsExactly(new TimeWindow(LocalTime.of(9, 0), LocalTime.of(10, 0)));
    }

    @Test
    void elDomingoSeConsultaComoDia7() {
        UUID a = UUID.randomUUID();
        Set<UUID> ids = Set.of(a);
        when(repository.findByResourceIdInAndDayOfWeek(ids, 7)).thenReturn(List.of());

        assertThat(lookup.windowsOn(ids, DayOfWeek.SUNDAY)).isEmpty();
        verify(repository).findByResourceIdInAndDayOfWeek(ids, 7);
    }

    @Test
    void sinRecursosNoConsultaLaBase() {
        assertThat(lookup.windowsOn(Set.of(), DayOfWeek.MONDAY)).isEmpty();
        verify(repository, never()).findByResourceIdInAndDayOfWeek(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
