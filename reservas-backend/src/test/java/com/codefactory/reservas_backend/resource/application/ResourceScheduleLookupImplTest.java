package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.common.config.TimeConfig;
import com.codefactory.reservas_backend.resource.domain.ResourceAvailability;
import com.codefactory.reservas_backend.resource.domain.ResourceClosure;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceAvailabilityRepository;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceClosureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceScheduleLookupImplTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    @Mock
    private ResourceAvailabilityRepository repository;
    @Mock
    private ResourceClosureRepository closureRepository;

    private ResourceScheduleLookupImpl lookup;

    @BeforeEach
    void setUp() {
        lookup = new ResourceScheduleLookupImpl(repository, closureRepository);
    }

    private static ResourceAvailability row(UUID resourceId, int day, int startHour, int endHour) {
        return ResourceAvailability.builder().resourceId(resourceId).dayOfWeek(day)
                .startTime(LocalTime.of(startHour, 0)).endTime(LocalTime.of(endHour, 0)).build();
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(TimeConfig.BUSINESS_ZONE).toInstant();
    }

    private static ResourceClosure closure(UUID resourceId, Instant start, Instant end) {
        return ResourceClosure.builder().resourceId(resourceId).startAt(start).endAt(end).build();
    }

    private static TimeWindow window(int sh, int sm, int eh, int em) {
        return new TimeWindow(LocalTime.of(sh, sm), LocalTime.of(eh, em));
    }

    @Test
    void debeAgruparPorRecursoYOrdenarLosRangosPorHoraDeInicio() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Set<UUID> ids = Set.of(a, b);
        when(repository.findByResourceIdInAndDayOfWeek(ids, 1)).thenReturn(List.of(
                row(a, 1, 14, 18), row(b, 1, 9, 10), row(a, 1, 9, 12)));

        Map<UUID, List<TimeWindow>> result = lookup.windowsOn(ids, DayOfWeek.MONDAY);

        assertThat(result.get(a)).containsExactly(window(9, 0, 12, 0), window(14, 0, 18, 0));
        assertThat(result.get(b)).containsExactly(window(9, 0, 10, 0));
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
        verify(repository, never()).findByResourceIdInAndDayOfWeek(any(), anyInt());
    }

    // --- HU-21: horario efectivo (semanal menos cierres) ---

    @Test
    void unCierreParcialPartaElRangoEnDos() {
        UUID a = UUID.randomUUID();
        when(repository.findByResourceIdInAndDayOfWeek(List.of(a), 1)).thenReturn(List.of(row(a, 1, 8, 18)));
        when(closureRepository.findTouching(anyCollection(), any(), any()))
                .thenReturn(List.of(closure(a, at(MONDAY, 12, 0), at(MONDAY, 14, 30))));

        Map<UUID, List<TimeWindow>> result = lookup.effectiveWindowsOn(List.of(a), MONDAY);

        assertThat(result.get(a)).containsExactly(window(8, 0, 12, 0), window(14, 30, 18, 0));
    }

    @Test
    void unCierreDeDiaCompletoDejaAlRecursoFueraDelMapa() {
        UUID a = UUID.randomUUID();
        when(repository.findByResourceIdInAndDayOfWeek(List.of(a), 1)).thenReturn(List.of(row(a, 1, 8, 12), row(a, 1, 14, 18)));
        when(closureRepository.findTouching(anyCollection(), any(), any()))
                .thenReturn(List.of(closure(a, at(MONDAY, 0, 0), at(MONDAY.plusDays(1), 0, 0))));

        assertThat(lookup.effectiveWindowsOn(List.of(a), MONDAY)).isEmpty();
    }

    @Test
    void unCierreQueEmpiezaElDiaAnteriorCierraHastaSuFinYDejaElResto() {
        UUID a = UUID.randomUUID();
        when(repository.findByResourceIdInAndDayOfWeek(List.of(a), 1)).thenReturn(List.of(row(a, 1, 8, 18)));
        when(closureRepository.findTouching(anyCollection(), any(), any()))
                .thenReturn(List.of(closure(a, at(MONDAY.minusDays(1), 20, 0), at(MONDAY, 10, 0))));

        assertThat(lookup.effectiveWindowsOn(List.of(a), MONDAY).get(a)).containsExactly(window(10, 0, 18, 0));
    }

    @Test
    void unCierreFueraDelHorarioNoCambiaNadaYElBordeCompartidoNoQuitaTiempo() {
        UUID a = UUID.randomUUID();
        when(repository.findByResourceIdInAndDayOfWeek(List.of(a), 1)).thenReturn(List.of(row(a, 1, 8, 12)));
        when(closureRepository.findTouching(anyCollection(), any(), any())).thenReturn(List.of(
                closure(a, at(MONDAY, 12, 0), at(MONDAY, 13, 0)),
                closure(a, at(MONDAY, 6, 0), at(MONDAY, 8, 0))));

        assertThat(lookup.effectiveWindowsOn(List.of(a), MONDAY).get(a)).containsExactly(window(8, 0, 12, 0));
    }

    @Test
    void sinHorarioEseDiaNoConsultaLosCierres() {
        UUID a = UUID.randomUUID();
        when(repository.findByResourceIdInAndDayOfWeek(List.of(a), 1)).thenReturn(List.of());

        assertThat(lookup.effectiveWindowsOn(List.of(a), MONDAY)).isEmpty();
        verify(closureRepository, never()).findTouching(anyCollection(), any(), any());
    }

    @Test
    void isClosedPreguntaDirectoALaBase() {
        UUID a = UUID.randomUUID();
        Instant s = at(MONDAY, 9, 0);
        Instant e = at(MONDAY, 10, 0);
        when(closureRepository.existsOverlapping(a, s, e)).thenReturn(true);

        assertThat(lookup.isClosed(a, s, e)).isTrue();
    }

    // --- Reportes: minutos disponibles por día ---

    @Test
    void minutosDisponiblesPorDiaRestanLosCierresYOmitenLosDiasSinAtencion() {
        UUID a = UUID.randomUUID();
        // Lunes 8-12 (240 min) y martes 9-10 (60 min); el resto de días sin horario.
        lenient().when(repository.findByResourceIdInAndDayOfWeek(eq(List.of(a)), anyInt())).thenReturn(List.of());
        when(repository.findByResourceIdInAndDayOfWeek(List.of(a), 1)).thenReturn(List.of(row(a, 1, 8, 12)));
        when(repository.findByResourceIdInAndDayOfWeek(List.of(a), 2)).thenReturn(List.of(row(a, 2, 9, 10)));
        // Cierre del lunes de 10:00 a 11:00.
        when(closureRepository.findTouching(anyCollection(), any(), any()))
                .thenReturn(List.of(closure(a, at(MONDAY, 10, 0), at(MONDAY, 11, 0))));

        Map<UUID, Map<LocalDate, Long>> result = lookup.availableMinutesByDay(List.of(a), MONDAY, MONDAY.plusDays(7));

        assertThat(result.get(a)).containsExactly(
                Map.entry(MONDAY, 180L), Map.entry(MONDAY.plusDays(1), 60L), Map.entry(MONDAY.plusDays(7), 240L));
    }

    @Test
    void minutosDisponiblesSinRecursosOPeriodoInvertidoDaVacio() {
        assertThat(lookup.availableMinutesByDay(List.of(), MONDAY, MONDAY)).isEmpty();
        assertThat(lookup.availableMinutesByDay(List.of(UUID.randomUUID()), MONDAY, MONDAY.minusDays(1))).isEmpty();
    }
}
