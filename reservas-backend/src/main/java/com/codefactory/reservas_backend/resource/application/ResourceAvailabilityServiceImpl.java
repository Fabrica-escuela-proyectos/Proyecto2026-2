package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.AvailabilityResponse;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.DaySchedule;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.DayScheduleRequest;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.TimeRange;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.WeekScheduleRequest;
import com.codefactory.reservas_backend.resource.domain.InvalidAvailabilityException;
import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.domain.ResourceAvailability;
import com.codefactory.reservas_backend.resource.domain.ResourceNotFoundException;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceAvailabilityRepository;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceAvailabilityServiceImpl implements ResourceAvailabilityService {

    static final String INVALID_RANGE_MESSAGE = "El rango horario no es válido: la hora de inicio debe ser anterior a la de fin";
    static final String OVERLAP_MESSAGE = "Los rangos horarios de un mismo día no pueden superponerse";
    static final String INVALID_DAY_MESSAGE = "El día debe estar entre 1 (lunes) y 7 (domingo)";
    static final String DUPLICATE_DAY_MESSAGE = "Cada día de la semana solo puede aparecer una vez";

    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private final ResourceRepository resourceRepository;
    private final ResourceAvailabilityRepository availabilityRepository;
    private final BusinessAccessService businessAccessService;
    private final AuditService auditService;

    @Override
    @Transactional
    public AvailabilityResponse replaceWeek(UUID resourceId, WeekScheduleRequest request, UserIdentity requester, String originIp) {
        Resource resource = lockOwnedResource(resourceId, requester);

        // Se valida TODO antes de tocar la base: si algo falla no cambia nada.
        List<ResourceAvailability> rows = new ArrayList<>();
        Set<Integer> seenDays = new HashSet<>();
        for (DayScheduleRequest day : request.days()) {
            requireValidDay(day.dayOfWeek());
            if (!seenDays.add(day.dayOfWeek())) {
                throw new InvalidAvailabilityException(DUPLICATE_DAY_MESSAGE);
            }
            rows.addAll(toRows(resource.getId(), day.dayOfWeek(), day.ranges()));
        }

        availabilityRepository.deleteAllOfResource(resourceId);
        availabilityRepository.saveAll(rows);
        audit(requester, originIp, "Horario semanal del recurso " + resourceId + " redefinido (" + rows.size() + " rango(s))");
        return buildResponse(resourceId, rows);
    }

    @Override
    @Transactional
    public AvailabilityResponse replaceDay(UUID resourceId, int dayOfWeek, List<TimeRange> ranges,
                                           UserIdentity requester, String originIp) {
        Resource resource = lockOwnedResource(resourceId, requester);
        requireValidDay(dayOfWeek);
        List<ResourceAvailability> rows = toRows(resource.getId(), dayOfWeek, ranges);

        availabilityRepository.deleteDayOfResource(resourceId, dayOfWeek);
        availabilityRepository.saveAll(rows);
        audit(requester, originIp, "Horario del día " + dayOfWeek + " del recurso " + resourceId + " redefinido ("
                + rows.size() + " rango(s))");
        return buildResponse(resourceId, availabilityRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(resourceId));
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResponse get(UUID resourceId, UserIdentity requester) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("El recurso solicitado no existe"));
        businessAccessService.requireOwner(resource.getBusinessId(), requester);
        return buildResponse(resourceId, availabilityRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(resourceId));
    }

    private Resource lockOwnedResource(UUID resourceId, UserIdentity requester) {
        // Bloqueo de fila: dos ediciones simultáneas del horario de un recurso se ejecutan una tras otra.
        Resource resource = resourceRepository.findByIdForUpdate(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("El recurso solicitado no existe"));
        businessAccessService.requireOwner(resource.getBusinessId(), requester);
        return resource;
    }

    private static void requireValidDay(Integer dayOfWeek) {
        if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) {
            throw new InvalidAvailabilityException(INVALID_DAY_MESSAGE);
        }
    }

    /** Convierte y valida los rangos de un día: inicio < fin y sin superposición (el borde compartido es válido). */
    private static List<ResourceAvailability> toRows(UUID resourceId, int dayOfWeek, List<TimeRange> ranges) {
        List<ResourceAvailability> rows = new ArrayList<>();
        for (TimeRange range : ranges) {
            LocalTime start = LocalTime.parse(range.start());
            LocalTime end = LocalTime.parse(range.end());
            if (!start.isBefore(end)) {
                throw new InvalidAvailabilityException(INVALID_RANGE_MESSAGE);
            }
            rows.add(ResourceAvailability.builder()
                    .resourceId(resourceId).dayOfWeek(dayOfWeek).startTime(start).endTime(end).build());
        }
        rows.sort(Comparator.comparing(ResourceAvailability::getStartTime));
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).getStartTime().isBefore(rows.get(i - 1).getEndTime())) {
                throw new InvalidAvailabilityException(OVERLAP_MESSAGE);
            }
        }
        return rows;
    }

    private void audit(UserIdentity requester, String originIp, String detail) {
        auditService.registerEvent(AuditEventType.DISPONIBILIDAD_RECURSO, requester.email(), "SUCCESS", detail, originIp);
    }

    private static AvailabilityResponse buildResponse(UUID resourceId, List<ResourceAvailability> rows) {
        List<DaySchedule> days = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            final int current = day;
            List<TimeRange> ranges = rows.stream()
                    .filter(r -> r.getDayOfWeek() == current)
                    .sorted(Comparator.comparing(ResourceAvailability::getStartTime))
                    .map(r -> new TimeRange(r.getStartTime().format(HOUR_MINUTE), r.getEndTime().format(HOUR_MINUTE)))
                    .toList();
            days.add(new DaySchedule(day, ranges));
        }
        return new AvailabilityResponse(resourceId, TIMEZONE, days);
    }
}
