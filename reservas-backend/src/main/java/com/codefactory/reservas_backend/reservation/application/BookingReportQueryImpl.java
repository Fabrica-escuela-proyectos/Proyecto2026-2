package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingReportQueryImpl implements BookingReportQuery {

    private final BookingRepository bookingRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countByStatus(UUID businessId, Instant from, Instant to) {
        return toMap(businessId == null
                ? bookingRepository.countByStatusInPlatform(from, to)
                : bookingRepository.countByStatusInBusiness(businessId, from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> cancelledByOrigin(UUID businessId, Instant from, Instant to) {
        return toMap(businessId == null
                ? bookingRepository.cancelledByOriginInPlatform(from, to)
                : bookingRepository.cancelledByOriginInBusiness(businessId, from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> valueByStatus(UUID businessId, Instant from, Instant to) {
        return toMap(businessId == null
                ? bookingRepository.valueByStatusInPlatform(from, to)
                : bookingRepository.valueByStatusInBusiness(businessId, from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceCount> countByService(UUID businessId, Instant from, Instant to) {
        return bookingRepository.countByServiceInBusiness(businessId, from, to).stream()
                .map(r -> new ServiceCount((UUID) r[0], (String) r[1], ((Number) r[2]).longValue(), ((Number) r[3]).longValue()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccupiedMinutes> occupiedMinutesByDay(UUID businessId, Instant from, Instant to) {
        return (businessId == null
                ? bookingRepository.occupiedMinutesInPlatform(from, to)
                : bookingRepository.occupiedMinutesInBusiness(businessId, from, to)).stream()
                .map(r -> new OccupiedMinutes(toUuid(r[0]), toDate(r[1]), ((Number) r[2]).longValue()))
                .toList();
    }

    private static UUID toUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    /** Según el driver, el DATE llega como java.sql.Date o como LocalDate. */
    private static LocalDate toDate(Object value) {
        return value instanceof java.sql.Date sql ? sql.toLocalDate() : (LocalDate) value;
    }

    /** Filas [clave (enum), número] → mapa ordenado con el nombre del enum. */
    private static Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                result.put(String.valueOf(row[0]), row[1] == null ? 0L : ((Number) row[1]).longValue());
            }
        }
        return result;
    }
}
