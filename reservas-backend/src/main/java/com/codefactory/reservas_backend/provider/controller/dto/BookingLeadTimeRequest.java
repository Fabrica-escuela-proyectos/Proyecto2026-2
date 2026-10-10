package com.codefactory.reservas_backend.provider.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Cuerpo de PUT /api/v1/businesses/{businessId}/booking-lead-time (HU-08). */
@Getter
@Setter
public class BookingLeadTimeRequest {

    @Schema(description = "Horas enteras de antelación mínima para reservar (1 a 720)", example = "2")
    @NotNull(message = "La antelación mínima es obligatoria")
    @Min(value = 1, message = "La antelación mínima debe ser un número entero de horas mayor o igual a 1")
    @Max(value = 720, message = "La antelación mínima no puede superar 720 horas (30 días)")
    private Integer hours;
}
