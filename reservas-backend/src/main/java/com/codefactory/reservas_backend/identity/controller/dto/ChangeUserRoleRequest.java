package com.codefactory.reservas_backend.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload de PATCH /api/v1/users/{userId}/role (HU-05). Campo según
 * dtos-sprint-1.md sección 6 ("ChangeUserRoleRequest"): role.
 */
@Getter
@Setter
public class ChangeUserRoleRequest {

    @Schema(description = "Rol nuevo: CLIENTE, PROVEEDOR o ADMINISTRADOR (el rol de un proveedor no se puede cambiar)", example = "CLIENTE")
    @NotBlank(message = "El rol es obligatorio")
    @Size(max = 30, message = "El rol no es válido")
    private String role;
}
