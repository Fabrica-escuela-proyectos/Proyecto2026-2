package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.common.config.TimeConfig;
import com.codefactory.reservas_backend.common.error.InvalidPaginationException;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingItem;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingPageResponse;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BusinessBookingPageResponse;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.InvalidBookingException;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingQueryServiceImpl implements BookingQueryService {

    static final String NO_BOOKINGS_MESSAGE = "No tiene reservas registradas";
    static final String NO_BOOKINGS_WITH_STATUS_MESSAGE = "No tiene reservas con el estado indicado";
    static final String NO_BUSINESS_BOOKINGS_MESSAGE = "No hay reservas registradas";
    static final String NO_BUSINESS_BOOKINGS_FILTERED_MESSAGE = "No hay reservas con los filtros indicados";
    static final String INVALID_DATE_MESSAGE = "La fecha no es válida: use el formato yyyy-MM-dd";
    static final String INVALID_DATE_RANGE_MESSAGE = "La fecha inicial no puede ser posterior a la final";
    static final String INVALID_STATUS_MESSAGE = "El estado no es válido: use CONFIRMADA, CANCELADA o COMPLETADA";

    private final BookingRepository bookingRepository;
    private final BusinessAccessService businessAccessService;

    @Override
    @Transactional(readOnly = true)
    public BookingPageResponse listOwn(UserIdentity client, String status, int page, int size) {
        requireValidPaging(page, size);
        BookingStatus filter = parseStatus(status);

        Page<Booking> result = filter == null
                ? bookingRepository.findByClientId(client.id(), newestFirst(page, size))
                : bookingRepository.findByClientIdAndStatus(client.id(), filter, newestFirst(page, size));

        List<BookingItem> items = result.getContent().stream().map(BookingMapper::toItem).toList();
        String message = null;
        if (result.getTotalElements() == 0) {
            message = filter == null ? NO_BOOKINGS_MESSAGE : NO_BOOKINGS_WITH_STATUS_MESSAGE;
        }
        return new BookingPageResponse(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), message);
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessBookingPageResponse listForBusiness(UUID businessId, UserIdentity provider, String from, String to,
                                                       String status, int page, int size) {
        businessAccessService.requireOwner(businessId, provider);
        requireValidPaging(page, size);
        BookingStatus filter = parseStatus(status);
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new InvalidBookingException(INVALID_DATE_RANGE_MESSAGE);
        }

        // Las fechas son días de Bogotá: [from 00:00, to + 1 día 00:00) sobre la hora de inicio de la reserva.
        Specification<Booking> spec = (root, query, cb) -> cb.equal(root.get("businessId"), businessId);
        if (filter != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter));
        }
        if (fromDate != null) {
            Instant fromInstant = fromDate.atStartOfDay(TimeConfig.BUSINESS_ZONE).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("startAt"), fromInstant));
        }
        if (toDate != null) {
            Instant toInstant = toDate.plusDays(1).atStartOfDay(TimeConfig.BUSINESS_ZONE).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.<Instant>get("startAt"), toInstant));
        }

        Page<Booking> result = bookingRepository.findAll(spec, newestFirst(page, size));

        boolean filtered = filter != null || fromDate != null || toDate != null;
        String message = null;
        if (result.getTotalElements() == 0) {
            message = filtered ? NO_BUSINESS_BOOKINGS_FILTERED_MESSAGE : NO_BUSINESS_BOOKINGS_MESSAGE;
        }
        return new BusinessBookingPageResponse(result.getContent().stream().map(BookingMapper::toBusinessItem).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), message);
    }

    /** De la más reciente a la más antigua por fecha de inicio; el id desempata para que la paginación sea estable. */
    private static PageRequest newestFirst(int page, int size) {
        return PageRequest.of(page, Math.min(size, MAX_SIZE), Sort.by(Sort.Order.desc("startAt"), Sort.Order.desc("id")));
    }

    private static void requireValidPaging(int page, int size) {
        if (page < 0) {
            throw new InvalidPaginationException("La página debe ser mayor o igual a 0");
        }
        if (size < 1) {
            throw new InvalidPaginationException("El tamaño de página debe ser mayor o igual a 1");
        }
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidBookingException(INVALID_DATE_MESSAGE);
        }
    }

    private static BookingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BookingStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidBookingException(INVALID_STATUS_MESSAGE);
        }
    }
}
