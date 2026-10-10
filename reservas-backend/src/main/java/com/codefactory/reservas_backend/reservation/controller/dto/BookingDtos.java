package com.codefactory.reservas_backend.reservation.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos de las reservas (HU-22). Fecha {@code yyyy-MM-dd} y horas {@code HH:mm} en hora local de Bogotá. */
public final class BookingDtos {

    static final String TIME_MESSAGE = "La hora debe tener el formato HH:mm (00:00 a 23:59)";
    static final String TIME_REGEX = "^([01]\\d|2[0-3]):[0-5]\\d$";

    private BookingDtos() {
    }

    /**
     * Cuerpo de POST /api/v1/bookings. {@code resourceId} es opcional: sin él el sistema
     * elige el primer recurso libre (por nombre). El cliente sale de la sesión, nunca del cuerpo.
     */
    @Getter
    @Setter
    public static class CreateBookingRequest {

        @NotNull(message = "El servicio es obligatorio")
        private UUID serviceId;

        @NotBlank(message = "La fecha es obligatoria")
        private String date;

        @NotBlank(message = "La hora de inicio es obligatoria")
        @Pattern(regexp = TIME_REGEX, message = TIME_MESSAGE)
        private String startTime;

        @NotBlank(message = "La hora de fin es obligatoria")
        @Pattern(regexp = TIME_REGEX, message = TIME_MESSAGE)
        private String endTime;

        private UUID resourceId;
    }

    /**
     * Reserva en un listado (HU-23; HU-24 la reutiliza). Para una CANCELADA trae el motivo y la
     * fecha de cancelación; en las demás ambos vienen nulos.
     */
    public record BookingItem(UUID id, String status, UUID serviceId, String serviceName, UUID businessId,
                              String businessName, UUID resourceId, String resourceName, String date,
                              String startTime, String endTime, long priceCop, String cancelOrigin,
                              String cancelReason, Instant cancelledAt, Instant createdAt) {
    }

    /** Página de reservas (numeración desde 0); {@code message} solo viene cuando no hay ninguna. */
    public record BookingPageResponse(List<BookingItem> items, int page, int size, long totalElements,
                                      int totalPages, String message) {
    }

    /**
     * Reserva vista por el PROVEEDOR del negocio (HU-24): trae los datos del cliente que la reserva
     * conserva (id, nombre y correo, copiados al reservar; {@code clientId} es nulo si el cliente
     * eliminó su cuenta) y los del servicio y recurso. No incluye su celular: la reserva no lo guarda.
     */
    public record BusinessBookingItem(UUID id, String status, UUID clientId, String clientName, String clientEmail,
                                      UUID serviceId, String serviceName,
                                      UUID resourceId, String resourceName, String date, String startTime,
                                      String endTime, long priceCop, String cancelOrigin, String cancelReason,
                                      Instant cancelledAt, Instant createdAt) {
    }

    /** Página de reservas de un negocio; {@code message} solo viene cuando no hay ninguna. */
    public record BusinessBookingPageResponse(List<BusinessBookingItem> items, int page, int size, long totalElements,
                                              int totalPages, String message) {
    }

    /** Cuerpo opcional de POST /api/v1/bookings/{bookingId}/cancellation (HU-25). */
    @Getter
    @Setter
    public static class CancelBookingRequest {

        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        private String reason;
    }

    /** Cuerpo de POST /api/v1/bookings/{bookingId}/provider-cancellation (HU-26): el motivo es obligatorio. */
    @Getter
    @Setter
    public static class ProviderCancelRequest {

        @NotBlank(message = "El motivo de la cancelación es obligatorio")
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        private String reason;
    }

    /** Reserva creada; {@code id} es el que devuelve HU-22 y usan HU-23..28. */
    public record BookingResponse(UUID id, String status, UUID serviceId, String serviceName, UUID businessId,
                                  String businessName, UUID resourceId, String resourceName, String date,
                                  String startTime, String endTime, long priceCop, Instant createdAt) {
    }
}
