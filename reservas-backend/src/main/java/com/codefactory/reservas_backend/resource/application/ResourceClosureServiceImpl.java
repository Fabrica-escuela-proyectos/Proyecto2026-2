package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.common.config.TimeConfig;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.resource.domain.ClosureConflictException;
import com.codefactory.reservas_backend.resource.domain.ClosureNotFoundException;
import com.codefactory.reservas_backend.resource.domain.InvalidClosureException;
import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.domain.ResourceClosure;
import com.codefactory.reservas_backend.resource.domain.ResourceNotFoundException;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceClosureRepository;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceClosureServiceImpl implements ResourceClosureService {

    static final String INVALID_PERIOD_MESSAGE = "El periodo de cierre no es válido: envíe una fecha (date) "
            + "o un inicio y un fin (start, end) con el formato yyyy-MM-dd HH:mm";
    static final String INVALID_ORDER_MESSAGE = "El periodo de cierre no es válido: el fin debe ser posterior al inicio";
    static final String PAST_MESSAGE = "La fecha del cierre debe ser igual o posterior a la fecha actual";
    static final String TOO_LONG_MESSAGE = "El periodo de cierre no puede superar 366 días";
    static final String OVERLAP_MESSAGE = "Ya existe un cierre programado que se cruza con ese periodo";
    static final String STARTED_MESSAGE = "El cierre ya comenzó y no se puede eliminar";
    static final long MAX_DAYS = 366;

    private static final DateTimeFormatter LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final ResourceClosureRepository closureRepository;
    private final ResourceRepository resourceRepository;
    private final BusinessAccessService businessAccessService;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    public ClosurePeriod resolvePeriod(String date, String start, String end) {
        boolean hasDate = date != null && !date.isBlank();
        boolean hasStart = start != null && !start.isBlank();
        boolean hasEnd = end != null && !end.isBlank();

        LocalDateTime from;
        LocalDateTime to;
        if (hasDate && !hasStart && !hasEnd) {
            from = parseDate(date).atStartOfDay();
            to = from.plusDays(1);
        } else if (!hasDate && hasStart && hasEnd) {
            from = parseDateTime(start);
            to = parseDateTime(end);
        } else {
            throw new InvalidClosureException(INVALID_PERIOD_MESSAGE);
        }

        if (!to.isAfter(from)) {
            throw new InvalidClosureException(INVALID_ORDER_MESSAGE);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (from.toLocalDate().isBefore(now.toLocalDate()) || !to.isAfter(now)) {
            throw new InvalidClosureException(PAST_MESSAGE);
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new InvalidClosureException(TOO_LONG_MESSAGE);
        }
        return new ClosurePeriod(from.atZone(TimeConfig.BUSINESS_ZONE).toInstant(),
                to.atZone(TimeConfig.BUSINESS_ZONE).toInstant());
    }

    @Override
    @Transactional(readOnly = true)
    public void requireNoOverlap(UUID resourceId, ClosurePeriod period) {
        if (closureRepository.existsOverlapping(resourceId, period.start(), period.end())) {
            throw new ClosureConflictException(OVERLAP_MESSAGE);
        }
    }

    @Override
    @Transactional
    public ClosureInfo create(UUID resourceId, ClosurePeriod period, String reason, int cancelledBookings,
                              UserIdentity requester, String originIp) {
        // saveAndFlush: si dos cierres traslapados pasan la verificación a la vez, la restricción
        // ex_resource_closures_no_overlap falla aquí (SQLSTATE 23P01) y la API responde 409.
        ResourceClosure saved = closureRepository.saveAndFlush(ResourceClosure.builder()
                .resourceId(resourceId)
                .startAt(period.start())
                .endAt(period.end())
                .reason(reason == null || reason.isBlank() ? null : reason.trim())
                .createdBy(requester.id())
                .build());
        auditService.registerEvent(AuditEventType.CIERRE_RECURSO, requester.email(), "SUCCESS",
                "Recurso " + resourceId + " cerrado de " + format(saved.getStartAt()) + " a " + format(saved.getEndAt())
                        + " (" + cancelledBookings + " reserva(s) cancelada(s))", originIp);
        return toInfo(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClosureInfo> listUpcoming(UUID resourceId, UserIdentity requester) {
        requireOwned(resourceId, requester);
        return closureRepository.findByResourceIdAndEndAtAfterOrderByStartAtAsc(resourceId, clock.instant()).stream()
                .map(ResourceClosureServiceImpl::toInfo)
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID resourceId, UUID closureId, UserIdentity requester, String originIp) {
        // Bloqueo del recurso: serializa la eliminación con un cierre o una reserva simultáneos del mismo recurso.
        Resource resource = resourceRepository.findByIdForUpdate(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("El recurso solicitado no existe"));
        businessAccessService.requireOwner(resource.getBusinessId(), requester);

        ResourceClosure closure = closureRepository.findById(closureId)
                .filter(c -> c.getResourceId().equals(resourceId))
                .orElseThrow(() -> new ClosureNotFoundException("El cierre solicitado no existe"));
        if (!closure.getStartAt().isAfter(clock.instant())) {
            throw new ClosureConflictException(STARTED_MESSAGE);
        }
        closureRepository.delete(closure);
        closureRepository.flush();
        auditService.registerEvent(AuditEventType.ELIMINACION_CIERRE_RECURSO, requester.email(), "SUCCESS",
                "Cierre " + closureId + " del recurso " + resourceId + " eliminado (" + format(closure.getStartAt())
                        + " a " + format(closure.getEndAt()) + ")", originIp);
    }

    private void requireOwned(UUID resourceId, UserIdentity requester) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("El recurso solicitado no existe"));
        businessAccessService.requireOwner(resource.getBusinessId(), requester);
    }

    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidClosureException(INVALID_PERIOD_MESSAGE);
        }
    }

    /** Acepta {@code yyyy-MM-dd HH:mm} (como en el AC) y {@code yyyy-MM-ddTHH:mm}. */
    private static LocalDateTime parseDateTime(String text) {
        String normalized = text.trim().replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalized, LOCAL_FORMAT);
        } catch (DateTimeParseException e) {
            throw new InvalidClosureException(INVALID_PERIOD_MESSAGE);
        }
    }

    private static String format(Instant instant) {
        return LocalDateTime.ofInstant(instant, TimeConfig.BUSINESS_ZONE).format(LOCAL_FORMAT);
    }

    private static ClosureInfo toInfo(ResourceClosure c) {
        LocalDateTime start = LocalDateTime.ofInstant(c.getStartAt(), TimeConfig.BUSINESS_ZONE);
        LocalDateTime end = LocalDateTime.ofInstant(c.getEndAt(), TimeConfig.BUSINESS_ZONE);
        boolean fullDay = start.toLocalTime().equals(LocalTime.MIDNIGHT) && end.toLocalTime().equals(LocalTime.MIDNIGHT);
        return new ClosureInfo(c.getId(), c.getResourceId(), start.format(LOCAL_FORMAT), end.format(LOCAL_FORMAT),
                fullDay, c.getReason(), c.getCreatedAt());
    }
}
