package com.codefactory.reservas_backend.report.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.provider.application.BusinessDirectoryService;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.BookingValueReport;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.BookingsByStatusReport;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.BusinessOccupancyReport;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.PeriodOccupancy;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.PlatformSummaryReport;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.ResourceOccupancy;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.ResourceOccupancyReport;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.ServiceRanking;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.TopServicesReport;
import com.codefactory.reservas_backend.report.domain.InvalidReportPeriodException;
import com.codefactory.reservas_backend.reservation.application.BookingReportQuery;
import com.codefactory.reservas_backend.reservation.application.BookingReportQuery.OccupiedMinutes;
import com.codefactory.reservas_backend.reservation.application.BookingReportQuery.ServiceCount;
import com.codefactory.reservas_backend.resource.application.ResourceInfo;
import com.codefactory.reservas_backend.resource.application.ResourceLookupService;
import com.codefactory.reservas_backend.resource.application.ResourceScheduleLookup;
import com.codefactory.reservas_backend.service.application.ServiceInfo;
import com.codefactory.reservas_backend.service.application.ServiceLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    static final String NO_BOOKINGS_MESSAGE = "No hay reservas en el periodo seleccionado";
    static final String NO_SCHEDULE_MESSAGE = "El negocio no tiene recursos activos con horario de atención en el periodo seleccionado";
    static final String VALUE_NOTE = "Referencia calculada con el precio guardado en cada reserva; no corresponde a pagos ni cobros";
    static final String GROUP_MESSAGE = "El parámetro groupBy debe ser DAY o WEEK";
    static final String LIMIT_MESSAGE = "El parámetro limit debe estar entre 1 y " + MAX_TOP;

    private static final String CONFIRMED = "CONFIRMADA";
    private static final String CANCELLED = "CANCELADA";
    private static final String COMPLETED = "COMPLETADA";

    private final BusinessAccessService businessAccessService;
    private final BusinessDirectoryService businessDirectoryService;
    private final BookingReportQuery bookingReportQuery;
    private final ResourceLookupService resourceLookupService;
    private final ResourceScheduleLookup scheduleLookup;
    private final ServiceLookupService serviceLookupService;

    // --- HU-29: ocupación por recurso ---

    @Override
    @Transactional(readOnly = true)
    public ResourceOccupancyReport resourceOccupancy(UUID businessId, ReportPeriod period, UserIdentity requester) {
        businessAccessService.requireOwner(businessId, requester);
        List<ResourceInfo> resources = activeResources(businessId);
        Occupancy occupancy = occupancy(businessId, resources.stream().map(ResourceInfo::id).toList(), period);

        List<ResourceOccupancy> rows = resources.stream()
                .map(r -> {
                    long available = occupancy.availableOf(r.id());
                    long booked = occupancy.bookedOf(r.id());
                    return new ResourceOccupancy(r.id(), r.name(), r.type(), available, booked, percent(booked, available));
                })
                // De más a menos usado: la HU quiere ver cuáles se usan más y cuáles tienen horas libres.
                .sorted(Comparator.comparing((ResourceOccupancy o) -> o.occupancyPercent() == null ? -1.0 : o.occupancyPercent())
                        .reversed().thenComparing(ResourceOccupancy::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        long available = occupancy.totalAvailable();
        long booked = occupancy.totalBooked();
        return new ResourceOccupancyReport(businessId, period.from(), period.to(), rows, available, booked,
                percent(booked, available), emptyMessage(available, booked));
    }

    // --- HU-30: ocupación del negocio por día o semana ---

    @Override
    @Transactional(readOnly = true)
    public BusinessOccupancyReport businessOccupancy(UUID businessId, ReportPeriod period, String groupBy, UserIdentity requester) {
        boolean weekly = parseGroupBy(groupBy);
        businessAccessService.requireOwner(businessId, requester);
        Occupancy occupancy = occupancy(businessId, activeResources(businessId).stream().map(ResourceInfo::id).toList(), period);

        List<PeriodOccupancy> periods = new ArrayList<>();
        LocalDate cursor = period.from();
        while (!cursor.isAfter(period.to())) {
            LocalDate end = weekly ? min(cursor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)), period.to()) : cursor;
            long available = 0;
            long booked = 0;
            for (LocalDate d = cursor; !d.isAfter(end); d = d.plusDays(1)) {
                available += occupancy.availableOn(d);
                booked += occupancy.bookedOn(d);
            }
            periods.add(new PeriodOccupancy(cursor, end, available, booked, percent(booked, available)));
            cursor = end.plusDays(1);
        }

        // Mayor y menor demanda entre los periodos en que el negocio sí atendía.
        List<PeriodOccupancy> open = periods.stream().filter(p -> p.occupancyPercent() != null).toList();
        Comparator<PeriodOccupancy> byPercent = Comparator.comparing(PeriodOccupancy::occupancyPercent);
        PeriodOccupancy highest = open.stream().max(byPercent.thenComparing(PeriodOccupancy::start, Comparator.reverseOrder())).orElse(null);
        PeriodOccupancy lowest = open.stream().min(byPercent.thenComparing(PeriodOccupancy::start)).orElse(null);
        long available = occupancy.totalAvailable();
        long booked = occupancy.totalBooked();
        return new BusinessOccupancyReport(businessId, period.from(), period.to(), weekly ? "WEEK" : "DAY", periods,
                highest, lowest, percent(booked, available), emptyMessage(available, booked));
    }

    // --- HU-31: reservas por estado ---

    @Override
    @Transactional(readOnly = true)
    public BookingsByStatusReport bookingsByStatus(UUID businessId, ReportPeriod period, UserIdentity requester) {
        businessAccessService.requireOwner(businessId, requester);
        Map<String, Long> counts = bookingReportQuery.countByStatus(businessId, period.start(), period.end());
        long confirmed = counts.getOrDefault(CONFIRMED, 0L);
        long cancelled = counts.getOrDefault(CANCELLED, 0L);
        long completed = counts.getOrDefault(COMPLETED, 0L);
        long total = confirmed + cancelled + completed;
        Map<String, Long> byOrigin = new TreeMap<>(bookingReportQuery.cancelledByOrigin(businessId, period.start(), period.end()));
        return new BookingsByStatusReport(businessId, period.from(), period.to(), confirmed, cancelled, completed, total,
                byOrigin, total == 0 ? NO_BOOKINGS_MESSAGE : null);
    }

    // --- HU-32: servicios más reservados ---

    @Override
    @Transactional(readOnly = true)
    public TopServicesReport topServices(UUID businessId, ReportPeriod period, Integer limit, UserIdentity requester) {
        int top = limit == null ? DEFAULT_TOP : limit;
        if (top < 1 || top > MAX_TOP) {
            throw new InvalidReportPeriodException(LIMIT_MESSAGE);
        }
        businessAccessService.requireOwner(businessId, requester);

        List<ServiceCount> counts = bookingReportQuery.countByService(businessId, period.start(), period.end()).stream()
                .map(this::withCurrentName)
                .sorted(Comparator.comparingLong(ServiceCount::booked).reversed()
                        .thenComparingLong(ServiceCount::cancelled)
                        .thenComparing(ServiceCount::serviceName, String.CASE_INSENSITIVE_ORDER))
                .limit(top)
                .toList();
        List<ServiceRanking> ranking = new ArrayList<>();
        for (int i = 0; i < counts.size(); i++) {
            ServiceCount c = counts.get(i);
            ranking.add(new ServiceRanking(i + 1, c.serviceId(), c.serviceName(), c.booked(), c.cancelled()));
        }
        return new TopServicesReport(businessId, period.from(), period.to(), ranking, ranking.isEmpty() ? NO_BOOKINGS_MESSAGE : null);
    }

    /** El nombre vigente del servicio (pudo editarse, HU-10); si se eliminó, el guardado en la reserva. */
    private ServiceCount withCurrentName(ServiceCount c) {
        if (c.serviceId() == null) {
            return new ServiceCount(null, c.serviceName() + " (servicio eliminado)", c.booked(), c.cancelled());
        }
        String name = serviceLookupService.findById(c.serviceId()).map(ServiceInfo::name).orElse(c.serviceName());
        return new ServiceCount(c.serviceId(), name, c.booked(), c.cancelled());
    }

    // --- HU-33: valor de las reservas ---

    @Override
    @Transactional(readOnly = true)
    public BookingValueReport bookingValue(UUID businessId, ReportPeriod period, UserIdentity requester) {
        businessAccessService.requireOwner(businessId, requester);
        Map<String, Long> values = bookingReportQuery.valueByStatus(businessId, period.start(), period.end());
        Map<String, Long> counts = bookingReportQuery.countByStatus(businessId, period.start(), period.end());
        long completed = values.getOrDefault(COMPLETED, 0L);
        long confirmed = values.getOrDefault(CONFIRMED, 0L);
        long cancelled = values.getOrDefault(CANCELLED, 0L);
        boolean empty = counts.values().stream().mapToLong(Long::longValue).sum() == 0;
        // Total = lo que el negocio realizó (completadas) más lo que tiene comprometido (confirmadas); las canceladas aparte.
        return new BookingValueReport(businessId, period.from(), period.to(), "COP", completed, confirmed,
                completed + confirmed, cancelled, VALUE_NOTE, empty ? NO_BOOKINGS_MESSAGE : null);
    }

    // --- HU-34: resumen de la plataforma ---

    @Override
    @Transactional(readOnly = true)
    public PlatformSummaryReport platformSummary(ReportPeriod period) {
        Map<String, Long> counts = bookingReportQuery.countByStatus(null, period.start(), period.end());
        long confirmed = counts.getOrDefault(CONFIRMED, 0L);
        long cancelled = counts.getOrDefault(CANCELLED, 0L);
        long completed = counts.getOrDefault(COMPLETED, 0L);
        long total = confirmed + cancelled + completed;
        Occupancy occupancy = occupancy(null, resourceLookupService.allActiveIds(), period);
        long available = occupancy.totalAvailable();
        long booked = occupancy.totalBooked();
        return new PlatformSummaryReport(period.from(), period.to(), businessDirectoryService.countWithEnabledOwner(),
                confirmed, cancelled, completed, total, available, booked, percent(booked, available),
                total == 0 ? NO_BOOKINGS_MESSAGE : null);
    }

    // --- cálculo de ocupación (decisión D1 del plan del Sprint 3) ---

    /**
     * Cruza los minutos disponibles (horario efectivo de cada recurso, por día) con los ocupados por reservas
     * confirmadas o completadas. Solo cuentan los recursos indicados (los activos): una reserva en un recurso
     * que ya no está activo no suma minutos ocupados sin sumar también disponibles.
     */
    private Occupancy occupancy(UUID businessId, Collection<UUID> resourceIds, ReportPeriod period) {
        Map<UUID, Map<LocalDate, Long>> available = scheduleLookup.availableMinutesByDay(resourceIds, period.from(), period.to());
        Set<UUID> considered = new HashSet<>(resourceIds);
        List<OccupiedMinutes> booked = bookingReportQuery.occupiedMinutesByDay(businessId, period.start(), period.end()).stream()
                .filter(o -> considered.contains(o.resourceId()))
                .toList();
        return new Occupancy(available, booked);
    }

    private List<ResourceInfo> activeResources(UUID businessId) {
        return resourceLookupService.findAllOfBusiness(businessId).stream().filter(ResourceInfo::active).toList();
    }

    /** Porcentaje con un decimal; nulo si no hay tiempo disponible (no se puede dividir por cero). */
    static Double percent(long booked, long available) {
        if (available <= 0) {
            return null;
        }
        return BigDecimal.valueOf(booked * 100L).divide(BigDecimal.valueOf(available), 1, RoundingMode.HALF_UP).doubleValue();
    }

    private static String emptyMessage(long available, long booked) {
        if (available == 0 && booked == 0) {
            return NO_SCHEDULE_MESSAGE;
        }
        return booked == 0 ? NO_BOOKINGS_MESSAGE : null;
    }

    private static boolean parseGroupBy(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            return false;
        }
        return switch (groupBy.trim().toUpperCase(Locale.ROOT)) {
            case "DAY" -> false;
            case "WEEK" -> true;
            default -> throw new InvalidReportPeriodException(GROUP_MESSAGE);
        };
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    /** Minutos disponibles y ocupados del periodo, por recurso y por día. */
    private static final class Occupancy {

        private final Map<UUID, Long> availableByResource = new HashMap<>();
        private final Map<UUID, Long> bookedByResource = new HashMap<>();
        private final Map<LocalDate, Long> availableByDay = new HashMap<>();
        private final Map<LocalDate, Long> bookedByDay = new HashMap<>();

        Occupancy(Map<UUID, Map<LocalDate, Long>> available, List<OccupiedMinutes> booked) {
            available.forEach((resourceId, byDay) -> byDay.forEach((day, minutes) -> {
                availableByResource.merge(resourceId, minutes, Long::sum);
                availableByDay.merge(day, minutes, Long::sum);
            }));
            for (OccupiedMinutes o : booked) {
                bookedByResource.merge(o.resourceId(), o.minutes(), Long::sum);
                bookedByDay.merge(o.date(), o.minutes(), Long::sum);
            }
        }

        long availableOf(UUID resourceId) {
            return availableByResource.getOrDefault(resourceId, 0L);
        }

        long bookedOf(UUID resourceId) {
            return bookedByResource.getOrDefault(resourceId, 0L);
        }

        long availableOn(LocalDate day) {
            return availableByDay.getOrDefault(day, 0L);
        }

        long bookedOn(LocalDate day) {
            return bookedByDay.getOrDefault(day, 0L);
        }

        long totalAvailable() {
            return availableByResource.values().stream().mapToLong(Long::longValue).sum();
        }

        long totalBooked() {
            return bookedByResource.values().stream().mapToLong(Long::longValue).sum();
        }
    }
}
