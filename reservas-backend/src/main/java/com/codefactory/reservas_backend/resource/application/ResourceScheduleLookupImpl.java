package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.common.config.TimeConfig;
import com.codefactory.reservas_backend.resource.domain.ResourceAvailability;
import com.codefactory.reservas_backend.resource.domain.ResourceClosure;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceAvailabilityRepository;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceClosureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceScheduleLookupImpl implements ResourceScheduleLookup {

    private final ResourceAvailabilityRepository availabilityRepository;
    private final ResourceClosureRepository closureRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<TimeWindow>> windowsOn(Collection<UUID> resourceIds, DayOfWeek day) {
        Map<UUID, List<TimeWindow>> result = new LinkedHashMap<>();
        if (resourceIds.isEmpty()) {
            return result;
        }
        // DayOfWeek.getValue() ya es ISO-8601 (1 = lunes ... 7 = domingo), igual que la tabla.
        availabilityRepository.findByResourceIdInAndDayOfWeek(resourceIds, day.getValue()).stream()
                .sorted(Comparator.comparing(ResourceAvailability::getStartTime))
                .forEach(r -> result.computeIfAbsent(r.getResourceId(), k -> new ArrayList<>())
                        .add(new TimeWindow(r.getStartTime(), r.getEndTime())));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<TimeWindow>> effectiveWindowsOn(Collection<UUID> resourceIds, LocalDate date) {
        Map<UUID, List<TimeWindow>> weekly = windowsOn(resourceIds, date.getDayOfWeek());
        if (weekly.isEmpty()) {
            return weekly;
        }
        Map<UUID, List<ResourceClosure>> closures = closuresByResource(weekly.keySet(), date, date);
        Map<UUID, List<TimeWindow>> result = new LinkedHashMap<>();
        weekly.forEach((resourceId, windows) -> {
            List<TimeWindow> open = subtract(windows, closures.getOrDefault(resourceId, List.of()), date);
            if (!open.isEmpty()) {
                result.put(resourceId, open);
            }
        });
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isClosed(UUID resourceId, Instant start, Instant end) {
        return closureRepository.existsOverlapping(resourceId, start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Map<LocalDate, Long>> availableMinutesByDay(Collection<UUID> resourceIds, LocalDate from, LocalDate to) {
        Map<UUID, Map<LocalDate, Long>> result = new LinkedHashMap<>();
        if (resourceIds.isEmpty() || to.isBefore(from)) {
            return result;
        }
        // Dos consultas para todo el periodo (horario semanal y cierres); el cruce por día se hace en memoria.
        Map<UUID, Map<Integer, List<TimeWindow>>> weekly = new LinkedHashMap<>();
        for (int day = 1; day <= 7; day++) {
            int d = day;
            availabilityRepository.findByResourceIdInAndDayOfWeek(resourceIds, day).stream()
                    .sorted(Comparator.comparing(ResourceAvailability::getStartTime))
                    .forEach(r -> weekly.computeIfAbsent(r.getResourceId(), k -> new TreeMap<>())
                            .computeIfAbsent(d, k -> new ArrayList<>())
                            .add(new TimeWindow(r.getStartTime(), r.getEndTime())));
        }
        Map<UUID, List<ResourceClosure>> closures = closuresByResource(weekly.keySet(), from, to);

        weekly.forEach((resourceId, byDay) -> {
            Map<LocalDate, Long> minutes = new TreeMap<>();
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                List<TimeWindow> windows = byDay.getOrDefault(date.getDayOfWeek().getValue(), List.of());
                long total = subtract(windows, closures.getOrDefault(resourceId, List.of()), date).stream()
                        .mapToLong(w -> Duration.between(w.start(), w.end()).toMinutes())
                        .sum();
                if (total > 0) {
                    minutes.put(date, total);
                }
            }
            if (!minutes.isEmpty()) {
                result.put(resourceId, minutes);
            }
        });
        return result;
    }

    private Map<UUID, List<ResourceClosure>> closuresByResource(Collection<UUID> resourceIds, LocalDate from, LocalDate to) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        Instant start = from.atStartOfDay(TimeConfig.BUSINESS_ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(TimeConfig.BUSINESS_ZONE).toInstant();
        return closureRepository.findTouching(resourceIds, start, end).stream()
                .collect(Collectors.groupingBy(ResourceClosure::getResourceId));
    }

    /**
     * Quita de los rangos de un día los tramos cerrados. Cada cierre se recorta al día (en hora de
     * Bogotá); un cierre que sigue después de la medianoche cierra hasta el final del día.
     */
    static List<TimeWindow> subtract(List<TimeWindow> windows, List<ResourceClosure> closures, LocalDate date) {
        List<TimeWindow> open = new ArrayList<>(windows);
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        for (ResourceClosure closure : closures) {
            LocalDateTime cs = LocalDateTime.ofInstant(closure.getStartAt(), TimeConfig.BUSINESS_ZONE);
            LocalDateTime ce = LocalDateTime.ofInstant(closure.getEndAt(), TimeConfig.BUSINESS_ZONE);
            if (!cs.isBefore(dayEnd) || !ce.isAfter(dayStart)) {
                continue;
            }
            LocalTime from = cs.isAfter(dayStart) ? cs.toLocalTime() : LocalTime.MIN;
            LocalTime to = ce.isBefore(dayEnd) ? ce.toLocalTime() : LocalTime.MAX;
            List<TimeWindow> next = new ArrayList<>();
            for (TimeWindow w : open) {
                if (!to.isAfter(w.start()) || !from.isBefore(w.end())) {
                    next.add(w);
                    continue;
                }
                if (w.start().isBefore(from)) {
                    next.add(new TimeWindow(w.start(), from));
                }
                if (to.isBefore(w.end())) {
                    next.add(new TimeWindow(to, w.end()));
                }
            }
            open = next;
        }
        return open;
    }
}
