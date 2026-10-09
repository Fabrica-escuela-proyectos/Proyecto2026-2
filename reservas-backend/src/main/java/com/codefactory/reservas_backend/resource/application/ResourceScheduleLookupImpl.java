package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.resource.domain.ResourceAvailability;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceAvailabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceScheduleLookupImpl implements ResourceScheduleLookup {

    private final ResourceAvailabilityRepository availabilityRepository;

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
                .forEach(r -> result.computeIfAbsent(r.getResourceId(), k -> new java.util.ArrayList<>())
                        .add(new TimeWindow(r.getStartTime(), r.getEndTime())));
        return result;
    }
}
