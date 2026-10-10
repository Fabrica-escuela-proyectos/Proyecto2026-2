package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.common.config.TimeConfig;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingItem;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BusinessBookingItem;
import com.codefactory.reservas_backend.reservation.domain.Booking;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Convierte una reserva guardada (instantes UTC) a lo que ve el cliente: fecha y horas locales de Bogotá. */
public final class BookingMapper {

    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private BookingMapper() {
    }

    public static BusinessBookingItem toBusinessItem(Booking b) {
        ZonedDateTime start = b.getStartAt().atZone(TimeConfig.BUSINESS_ZONE);
        ZonedDateTime end = b.getEndAt().atZone(TimeConfig.BUSINESS_ZONE);
        return new BusinessBookingItem(b.getId(), b.getStatus().name(), b.getClientId(), b.getClientName(), b.getClientEmail(), b.getServiceId(),
                b.getServiceName(), b.getResourceId(), b.getResourceName(), start.toLocalDate().toString(),
                start.format(HOUR_MINUTE), end.format(HOUR_MINUTE), b.getPriceCop(), originOf(b), b.getCancelReason(),
                b.getCancelledAt(), b.getCreatedAt());
    }

    public static BookingItem toItem(Booking b) {
        ZonedDateTime start = b.getStartAt().atZone(TimeConfig.BUSINESS_ZONE);
        ZonedDateTime end = b.getEndAt().atZone(TimeConfig.BUSINESS_ZONE);
        return new BookingItem(b.getId(), b.getStatus().name(), b.getServiceId(), b.getServiceName(), b.getBusinessId(),
                b.getBusinessName(), b.getResourceId(), b.getResourceName(), start.toLocalDate().toString(),
                start.format(HOUR_MINUTE), end.format(HOUR_MINUTE), b.getPriceCop(), originOf(b), b.getCancelReason(),
                b.getCancelledAt(), b.getCreatedAt());
    }

    private static String originOf(Booking b) {
        return b.getCancelOrigin() == null ? null : b.getCancelOrigin().name();
    }
}
