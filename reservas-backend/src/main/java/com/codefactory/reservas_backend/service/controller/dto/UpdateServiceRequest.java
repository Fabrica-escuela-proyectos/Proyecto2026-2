package com.codefactory.reservas_backend.service.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de PATCH /api/v1/services/{serviceId} (HU-10). Edición parcial: un campo ausente o nulo no
 * cambia; una descripción vacía la borra; el nombre no puede quedar vacío. Mismos límites que al crear
 * (HU-09). Cambiar el precio no toca las reservas ya hechas: guardan su propio precio (snapshot).
 */
@Getter
@Setter
public class UpdateServiceRequest {

    @Schema(description = "Nuevo nombre; único por negocio (sin distinguir mayúsculas)", example = "Corte clásico")
    @Pattern(regexp = "(?s).*\\S.*", message = "El nombre del servicio es obligatorio")
    @Size(max = 150, message = "El nombre del servicio no puede superar los 150 caracteres")
    private String name;

    @Schema(description = "Nueva descripción (máx. 500); vacía = sin descripción", example = "Corte y lavado")
    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    private String description;

    @Schema(description = "Nueva duración en minutos (1 a 1440). Las reservas ya hechas conservan su horario", example = "60")
    @Min(value = 1, message = "La duración debe ser un número entero de minutos mayor que 0")
    @Max(value = 1440, message = "La duración no puede superar 1440 minutos (24 horas)")
    private Integer durationMinutes;

    @Schema(description = "Nuevo precio en COP, entero (0 = gratuito). Las reservas ya hechas conservan su precio", example = "60000")
    @Min(value = 0, message = "El precio no puede ser negativo")
    @Max(value = 1_000_000_000L, message = "El precio no puede superar 1.000.000.000 COP")
    private Long priceCop;
}
