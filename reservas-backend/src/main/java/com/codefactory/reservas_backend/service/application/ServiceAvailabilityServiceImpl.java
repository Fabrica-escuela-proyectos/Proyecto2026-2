package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.provider.application.BusinessDirectoryService;
import com.codefactory.reservas_backend.provider.application.BusinessSettingsService;
import com.codefactory.reservas_backend.resource.application.ResourceInfo;
import com.codefactory.reservas_backend.resource.application.ResourceLookupService;
import com.codefactory.reservas_backend.resource.application.ResourceScheduleLookup;
import com.codefactory.reservas_backend.resource.application.TimeWindow;
import com.codefactory.reservas_backend.service.controller.dto.ServiceAvailabilityDtos.ServiceAvailabilityResponse;
import com.codefactory.reservas_backend.service.controller.dto.ServiceAvailabilityDtos.Slot;
import com.codefactory.reservas_backend.service.controller.dto.ServiceAvailabilityDtos.SlotResource;
import com.codefactory.reservas_backend.service.domain.InvalidAvailabilityQueryException;
import com.codefactory.reservas_backend.service.domain.ServiceNotAvailableException;
import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import com.codefactory.reservas_backend.service.infrastructure.ServiceResourceRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ServiceAvailabilityServiceImpl implements ServiceAvailabilityService {

    static final String NOT_AVAILABLE_MESSAGE = "El servicio no está disponible";
    static final String INVALID_DATE_MESSAGE = "La fecha no es válida: use el formato yyyy-MM-dd";
    static final String PAST_DATE_MESSAGE = "Debe seleccionar una fecha futura o la fecha actual";
    static final String TOO_FAR_MESSAGE = "La fecha no puede superar los " + MAX_DAYS_AHEAD + " días desde hoy";
    static final String NO_SCHEDULE_MESSAGE = "No hay horarios disponibles ese día: el servicio no tiene horario definido";
    static final String NO_SLOTS_LEFT_MESSAGE = "No quedan horarios disponibles ese día";

    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private final ServiceOfferingRepository serviceRepository;
    private final ServiceResourceRepository assignmentRepository;
    private final ResourceLookupService resourceLookupService;
    private final ResourceScheduleLookup scheduleLookup;
    private final BusinessDirectoryService businessDirectoryService;
    private final BusinessSettingsService businessSettingsService;
    private final ObjectProvider<BusyTimeSource> busyTimeSources;
    private final Clock clock;

    public ServiceAvailabilityServiceImpl(ServiceOfferingRepository serviceRepository,
                                          ServiceResourceRepository assignmentRepository,
                                          ResourceLookupService resourceLookupService,
                                          ResourceScheduleLookup scheduleLookup,
                                          BusinessDirectoryService businessDirectoryService,
                                          BusinessSettingsService businessSettingsService,
                                          ObjectProvider<BusyTimeSource> busyTimeSources,
                                          Clock clock) {
        this.serviceRepository = serviceRepository;
        this.assignmentRepository = assignmentRepository;
        this.resourceLookupService = resourceLookupService;
        this.scheduleLookup = scheduleLookup;
        this.businessDirectoryService = businessDirectoryService;
        this.businessSettingsService = businessSettingsService;
        this.busyTimeSources = busyTimeSources;
        this.clock = clock;
    }

    @Override
    public ServiceAvailabilityResponse getAvailability(UUID serviceId, String dateText) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate date = parseDate(dateText, now.toLocalDate());

        ServiceOffering service = serviceRepository.findById(serviceId)
                .filter(ServiceOffering::isActive)
                .filter(s -> businessDirectoryService.isOwnerEnabled(s.getBusinessId()))
                .orElseThrow(() -> new ServiceNotAvailableException(NOT_AVAILABLE_MESSAGE));

        List<UUID> assignedIds = assignmentRepository.findByIdServiceId(serviceId).stream()
                .map(a -> a.getId().getResourceId())
                .toList();
        // Solo cuentan los recursos activos (HU-16/17 los desactivan sin quitarles la asignación).
        List<ResourceInfo> resources = resourceLookupService.findByIds(assignedIds).stream()
                .filter(ResourceInfo::active)
                .toList();
        List<UUID> resourceIds = resources.stream().map(ResourceInfo::id).toList();

        Map<UUID, List<TimeWindow>> windows = scheduleLookup.windowsOn(resourceIds, date.getDayOfWeek());
        if (windows.isEmpty()) {
            return response(service, date, List.of(), NO_SCHEDULE_MESSAGE);
        }

        LocalDateTime earliestStart = now.plusHours(businessSettingsService.minAdvanceHoursOf(service.getBusinessId()));
        List<BusyTimeSource.BusyInterval> busy = busyTimeSources.orderedStream()
                .flatMap(source -> source.busyOn(resourceIds, date).stream())
                .toList();
        Map<UUID, ResourceInfo> byId = resources.stream().collect(Collectors.toMap(ResourceInfo::id, r -> r));

        // Agrupa por hora de inicio: el mismo horario puede atenderse en varios recursos.
        Map<LocalTime, List<SlotResource>> slots = new TreeMap<>();
        int duration = service.getDurationMinutes();
        windows.forEach((resourceId, resourceWindows) -> {
            for (TimeWindow window : resourceWindows) {
                // Minutos enteros (no LocalTime): con duraciones de hasta 24 h LocalTime daría la vuelta a medianoche.
                int windowEnd = minutesOf(window.end());
                for (int startMin = minutesOf(window.start()); startMin + duration <= windowEnd; startMin += duration) {
                    LocalTime start = LocalTime.of(startMin / 60, startMin % 60);
                    LocalDateTime slotStart = date.atTime(start);
                    LocalDateTime slotEnd = slotStart.plusMinutes(duration);
                    if (slotStart.isBefore(earliestStart) || isBusy(busy, resourceId, slotStart, slotEnd)) {
                        continue;
                    }
                    ResourceInfo info = byId.get(resourceId);
                    slots.computeIfAbsent(start, k -> new ArrayList<>()).add(new SlotResource(info.id(), info.name()));
                }
            }
        });

        List<Slot> result = slots.entrySet().stream()
                .map(e -> new Slot(e.getKey().format(HOUR_MINUTE), e.getKey().plusMinutes(duration).format(HOUR_MINUTE),
                        e.getValue().stream()
                                .sorted(Comparator.comparing(SlotResource::name, String.CASE_INSENSITIVE_ORDER))
                                .toList()))
                .toList();
        return response(service, date, result, result.isEmpty() ? NO_SLOTS_LEFT_MESSAGE : null);
    }

    private static int minutesOf(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static boolean isBusy(List<BusyTimeSource.BusyInterval> busy, UUID resourceId,
                                  LocalDateTime slotStart, LocalDateTime slotEnd) {
        return busy.stream().anyMatch(b -> b.resourceId().equals(resourceId)
                && slotStart.isBefore(b.end()) && b.start().isBefore(slotEnd));
    }

    private LocalDate parseDate(String text, LocalDate today) {
        if (text == null || text.isBlank()) {
            return today;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidAvailabilityQueryException(INVALID_DATE_MESSAGE);
        }
        if (date.isBefore(today)) {
            throw new InvalidAvailabilityQueryException(PAST_DATE_MESSAGE);
        }
        if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new InvalidAvailabilityQueryException(TOO_FAR_MESSAGE);
        }
        return date;
    }

    private static ServiceAvailabilityResponse response(ServiceOffering service, LocalDate date, List<Slot> slots, String message) {
        return new ServiceAvailabilityResponse(service.getId(), service.getName(), date.toString(),
                "America/Bogota", service.getDurationMinutes(), slots, message);
    }
}
