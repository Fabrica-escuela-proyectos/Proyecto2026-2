package com.codefactory.reservas_backend.provider.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de PATCH /api/v1/businesses/{businessId} (HU-07). Edición parcial (decisión D6 del plan del
 * Sprint 3): un campo ausente o nulo no cambia; en los opcionales (descripción, dirección, tipo y teléfono)
 * una cadena vacía lo borra; el nombre es obligatorio y no puede quedar vacío.
 */
@Getter
@Setter
public class UpdateBusinessRequest {

    static final String TYPE_MESSAGE = "El tipo de negocio no es válido: use SALUD, BELLEZA, BIENESTAR, DEPORTE, "
            + "EDUCACION, CONSULTORIA, ENTRETENIMIENTO u OTRO";

    @Schema(description = "Nombre del negocio (no puede quedar vacío)", example = "Barbería Central")
    @Pattern(regexp = "(?s).*\\S.*", message = "El nombre del negocio es obligatorio")
    @Size(max = 150, message = "El nombre del negocio no puede superar los 150 caracteres")
    private String name;

    @Schema(description = "Descripción (máx. 500); vacía = se borra", example = "Cortes clásicos y barba")
    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    private String description;

    @Schema(description = "Dirección (máx. 200); vacía = se borra", example = "Calle 10 # 43-12, Medellín")
    @Size(max = 200, message = "La dirección no puede superar los 200 caracteres")
    private String address;

    @Schema(description = "Tipo de negocio; vacío = se borra", example = "BELLEZA",
            allowableValues = {"SALUD", "BELLEZA", "BIENESTAR", "DEPORTE", "EDUCACION", "CONSULTORIA", "ENTRETENIMIENTO", "OTRO"})
    @Pattern(regexp = "(?i)\\s*|\\s*(SALUD|BELLEZA|BIENESTAR|DEPORTE|EDUCACION|CONSULTORIA|ENTRETENIMIENTO|OTRO)\\s*",
            message = TYPE_MESSAGE)
    private String businessType;

    @Schema(description = "Teléfono: 10 dígitos, celular (3XXXXXXXXX) o fijo (60XXXXXXXX); vacío = se borra", example = "6045551234")
    @Pattern(regexp = "\\s*|\\s*(3\\d{9}|60\\d{8})\\s*",
            message = "El teléfono no es válido: use 10 dígitos, celular (3XXXXXXXXX) o fijo (60XXXXXXXX)")
    private String phone;
}
