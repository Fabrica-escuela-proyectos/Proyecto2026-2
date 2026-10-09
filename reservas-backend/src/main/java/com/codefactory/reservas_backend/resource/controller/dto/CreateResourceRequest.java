package com.codefactory.reservas_backend.resource.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de POST /api/v1/businesses/{businessId}/resources (HU-14). El negocio
 * sale de la ruta: cualquier {@code businessId} que llegue en el cuerpo se
 * ignora (escenario "Intentar asociar el recurso a otro negocio").
 */
@Getter
@Setter
public class CreateResourceRequest {

    @NotBlank(message = "El nombre del recurso es obligatorio")
    @Size(max = 150, message = "El nombre del recurso no puede superar los 150 caracteres")
    private String name;

    // El patrón acepta vacío a propósito: de ese caso se encarga @NotBlank y así el
    // mensaje de campo faltante es siempre el mismo.
    @NotBlank(message = "El tipo del recurso es obligatorio")
    @Pattern(regexp = "(?i)\\s*|SALA|EQUIPO|PERSONAL", message = "El tipo debe ser SALA, EQUIPO o PERSONAL")
    private String type;
}
