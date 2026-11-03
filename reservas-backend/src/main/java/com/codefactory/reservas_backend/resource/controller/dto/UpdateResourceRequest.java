package com.codefactory.reservas_backend.resource.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de PATCH /api/v1/resources/{resourceId} (HU-15). Edición parcial: un campo ausente o nulo no
 * cambia. El nombre y el tipo siguen siendo obligatorios, así que no se pueden dejar vacíos. No hay campo
 * de negocio: un {@code businessId} en el cuerpo se ignora (escenario "cambiar el negocio asociado").
 */
@Getter
@Setter
public class UpdateResourceRequest {

    @Schema(description = "Nuevo nombre; único por negocio (sin distinguir mayúsculas)", example = "Sala 2")
    @Pattern(regexp = "(?s).*\\S.*", message = "El nombre del recurso es obligatorio")
    @Size(max = 150, message = "El nombre del recurso no puede superar los 150 caracteres")
    private String name;

    @Schema(description = "Nuevo tipo", example = "EQUIPO", allowableValues = {"SALA", "EQUIPO", "PERSONAL"})
    @Pattern(regexp = "(?i)\\s*(SALA|EQUIPO|PERSONAL)\\s*", message = "El tipo debe ser SALA, EQUIPO o PERSONAL")
    private String type;
}
