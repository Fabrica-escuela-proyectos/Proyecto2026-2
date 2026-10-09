package com.codefactory.reservas_backend.reservation.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
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

    /** Reserva creada; {@code id} es el que devuelve HU-22 y usan HU-23..28. */
    public record BookingResponse(UUID id, String status, UUID serviceId, String serviceName, UUID businessId,
                                  String businessName, UUID resourceId, String resourceName, String date,
                                  String startTime, String endTime, long priceCop, Instant createdAt) {
    }
}
