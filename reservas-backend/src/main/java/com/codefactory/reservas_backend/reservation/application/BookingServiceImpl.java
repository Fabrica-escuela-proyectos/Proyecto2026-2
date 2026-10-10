package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.common.config.TimeConfig;
import com.codefactory.reservas_backend.identity.application.UserDirectoryService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessDirectoryService;
import com.codefactory.reservas_backend.provider.application.BusinessSettingsService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingResponse;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.CreateBookingRequest;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.InvalidBookingException;
import com.codefactory.reservas_backend.reservation.domain.SlotNotAvailableException;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.resource.application.ResourceInfo;
import com.codefactory.reservas_backend.resource.application.ResourceLookupService;
import com.codefactory.reservas_backend.resource.application.ResourceScheduleLookup;
import com.codefactory.reservas_backend.resource.application.TimeWindow;
import com.codefactory.reservas_backend.service.application.ServiceInfo;
import com.codefactory.reservas_backend.service.application.ServiceLookupService;
import com.codefactory.reservas_backend.service.domain.ServiceNotAvailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    static final String INVALID_DATE_MESSAGE = "La fecha no es válida: use el formato yyyy-MM-dd";
    static final String PAST_DATE_MESSAGE = "Debe seleccionar una fecha futura o la fecha actual";
    static final String INVALID_RANGE_MESSAGE = "El rango de horas es inválido: la hora de fin debe ser posterior a la de inicio";
    static final String SERVICE_NOT_AVAILABLE_MESSAGE = "El servicio no está disponible";
    static final String RESOURCE_NOT_AVAILABLE_MESSAGE = "El recurso indicado no está asignado al servicio o no está activo";
    static final String OCCUPIED_MESSAGE = "El horario seleccionado no está disponible: ya tiene una reserva";
    static final String OUT_OF_SCHEDULE_MESSAGE = "El horario seleccionado no está disponible: queda fuera del horario de atención";

    private final BookingRepository bookingRepository;
    private final UserDirectoryService userDirectoryService;
    private final ServiceLookupService serviceLookupService;
    private final ResourceLookupService resourceLookupService;
    private final ResourceScheduleLookup scheduleLookup;
    private final BusinessDirectoryService businessDirectoryService;
    private final BusinessSettingsService businessSettingsService;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional
    public BookingResponse create(CreateBookingRequest request, UserIdentity client, String originIp) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate date = parseDate(request.getDate());
        LocalTime start = LocalTime.parse(request.getStartTime());
        LocalTime end = LocalTime.parse(request.getEndTime());

        if (!end.isAfter(start)) {
            throw new InvalidBookingException(INVALID_RANGE_MESSAGE);
        }
        if (date.isBefore(now.toLocalDate())) {
            throw new InvalidBookingException(PAST_DATE_MESSAGE);
        }

        ServiceInfo service = serviceLookupService.findById(request.getServiceId())
                .filter(ServiceInfo::active)
                .filter(s -> businessDirectoryService.isOwnerEnabled(s.businessId()))
                .orElseThrow(() -> new ServiceNotAvailableException(SERVICE_NOT_AVAILABLE_MESSAGE));

        long minutes = Duration.between(start, end).toMinutes();
        if (minutes != service.durationMinutes()) {
            throw new InvalidBookingException("La reserva debe durar " + service.durationMinutes()
                    + " minutos, la duración del servicio");
        }
        int leadHours = businessSettingsService.minAdvanceHoursOf(service.businessId());
        if (date.atTime(start).isBefore(now.plusHours(leadHours))) {
            throw new InvalidBookingException("La reserva debe hacerse con al menos " + leadHours
                    + (leadHours == 1 ? " hora" : " horas") + " de antelación");
        }

        List<ResourceInfo> candidates = candidateResources(service, request.getResourceId());
        Map<UUID, List<TimeWindow>> windows = scheduleLookup.windowsOn(
                candidates.stream().map(ResourceInfo::id).toList(), date.getDayOfWeek());

        Instant startAt = date.atTime(start).atZone(TimeConfig.BUSINESS_ZONE).toInstant();
        Instant endAt = date.atTime(end).atZone(TimeConfig.BUSINESS_ZONE).toInstant();

        ResourceInfo chosen = null;
        boolean withinSchedule = false;
        for (ResourceInfo candidate : candidates) {
            if (!fitsInWindow(windows.get(candidate.id()), start, end)) {
                continue;
            }
            withinSchedule = true;
            if (!bookingRepository.existsConfirmedOverlapping(candidate.id(), startAt, endAt)) {
                chosen = candidate;
                break;
            }
        }
        if (chosen == null) {
            throw new SlotNotAvailableException(withinSchedule ? OCCUPIED_MESSAGE : OUT_OF_SCHEDULE_MESSAGE);
        }

        String businessName = businessDirectoryService.get(service.businessId()).name();
        // saveAndFlush: si dos reservas simultáneas pasan la verificación anterior, la restricción
        // ex_bookings_no_overlap de la base falla aquí (SQLSTATE 23P01) y la API responde 409.
        Booking saved = bookingRepository.saveAndFlush(Booking.builder()
                .clientId(client.id())
                .clientEmail(client.email())
                .clientName(userDirectoryService.findFullName(client.id()).orElse(client.email()))
                .businessId(service.businessId())
                .businessName(businessName)
                .serviceId(service.id())
                .serviceName(service.name())
                .resourceId(chosen.id())
                .resourceName(chosen.name())
                .startAt(startAt)
                .endAt(endAt)
                .status(BookingStatus.CONFIRMADA)
                .priceCop(service.priceCop())
                .build());

        auditService.registerEvent(AuditEventType.CREACION_RESERVA, client.email(), "SUCCESS",
                "Reserva " + saved.getId() + " del servicio " + service.id() + " en el recurso " + chosen.id()
                        + " (" + date + " " + start + "-" + end + ")", originIp);
        return new BookingResponse(saved.getId(), saved.getStatus().name(), service.id(), service.name(),
                service.businessId(), businessName, chosen.id(), chosen.name(), date.toString(),
                request.getStartTime(), request.getEndTime(), saved.getPriceCop(), saved.getCreatedAt());
    }

    /** Recursos activos del servicio, por nombre; si el cliente pidió uno, solo ese (debe estar asignado y activo). */
    private List<ResourceInfo> candidateResources(ServiceInfo service, UUID requestedResourceId) {
        List<ResourceInfo> active = resourceLookupService.findByIds(serviceLookupService.assignedResourceIds(service.id())).stream()
                .filter(ResourceInfo::active)
                .sorted(Comparator.comparing(ResourceInfo::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (requestedResourceId == null) {
            if (active.isEmpty()) {
                throw new SlotNotAvailableException(OUT_OF_SCHEDULE_MESSAGE);
            }
            return active;
        }
        List<ResourceInfo> requested = active.stream().filter(r -> r.id().equals(requestedResourceId)).toList();
        if (requested.isEmpty()) {
            throw new InvalidBookingException(RESOURCE_NOT_AVAILABLE_MESSAGE);
        }
        return requested;
    }

    private static boolean fitsInWindow(List<TimeWindow> windows, LocalTime start, LocalTime end) {
        return windows != null && windows.stream().anyMatch(w -> !w.start().isAfter(start) && !end.isAfter(w.end()));
    }

    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidBookingException(INVALID_DATE_MESSAGE);
        }
    }
}
