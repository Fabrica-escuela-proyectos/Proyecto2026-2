package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.common.error.InvalidPaginationException;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingItem;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingPageResponse;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.InvalidBookingException;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BookingQueryServiceImpl implements BookingQueryService {

    static final String NO_BOOKINGS_MESSAGE = "No tiene reservas registradas";
    static final String NO_BOOKINGS_WITH_STATUS_MESSAGE = "No tiene reservas con el estado indicado";
    static final String INVALID_STATUS_MESSAGE = "El estado no es válido: use CONFIRMADA, CANCELADA o COMPLETADA";

    private final BookingRepository bookingRepository;

    @Override
    @Transactional(readOnly = true)
    public BookingPageResponse listOwn(UserIdentity client, String status, int page, int size) {
        if (page < 0) {
            throw new InvalidPaginationException("La página debe ser mayor o igual a 0");
        }
        if (size < 1) {
            throw new InvalidPaginationException("El tamaño de página debe ser mayor o igual a 1");
        }
        BookingStatus filter = parseStatus(status);

        // De la más reciente a la más antigua por fecha de inicio; el id desempata para que la paginación sea estable.
        PageRequest pageable = PageRequest.of(page, Math.min(size, MAX_SIZE),
                Sort.by(Sort.Order.desc("startAt"), Sort.Order.desc("id")));
        Page<Booking> result = filter == null
                ? bookingRepository.findByClientId(client.id(), pageable)
                : bookingRepository.findByClientIdAndStatus(client.id(), filter, pageable);

        List<BookingItem> items = result.getContent().stream().map(BookingMapper::toItem).toList();
        String message = null;
        if (result.getTotalElements() == 0) {
            message = filter == null ? NO_BOOKINGS_MESSAGE : NO_BOOKINGS_WITH_STATUS_MESSAGE;
        }
        return new BookingPageResponse(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), message);
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
