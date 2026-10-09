package com.codefactory.reservas_backend.provider.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Cuerpo de PUT /api/v1/businesses/{businessId}/booking-lead-time (HU-08). */
@Getter
@Setter
public class BookingLeadTimeRequest {

    @NotNull(message = "La antelación mínima es obligatoria")
    @Min(value = 1, message = "La antelación mínima debe ser un número entero de horas mayor o igual a 1")
    @Max(value = 720, message = "La antelación mínima no puede superar 720 horas (30 días)")
    private Integer hours;
}
