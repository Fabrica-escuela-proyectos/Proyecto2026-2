package com.codefactory.reservas_backend.service.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de POST /api/v1/businesses/{businessId}/services (HU-09). El negocio
 * sale de la ruta, nunca del cuerpo. Topes de duración y precio: supuestos del
 * equipo (un día y mil millones de COP), a confirmar con QA/PO.
 */
@Getter
@Setter
public class CreateServiceRequest {

    @NotBlank(message = "El nombre del servicio es obligatorio")
    @Size(max = 150, message = "El nombre del servicio no puede superar los 150 caracteres")
    private String name;

    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    private String description;

    @NotNull(message = "La duración es obligatoria")
    @Min(value = 1, message = "La duración debe ser un número entero de minutos mayor que 0")
    @Max(value = 1440, message = "La duración no puede superar 1440 minutos (24 horas)")
    private Integer durationMinutes;

    @NotNull(message = "El precio es obligatorio")
    @Min(value = 0, message = "El precio no puede ser negativo")
    @Max(value = 1_000_000_000L, message = "El precio no puede superar 1.000.000.000 COP")
    private Long priceCop;
}
