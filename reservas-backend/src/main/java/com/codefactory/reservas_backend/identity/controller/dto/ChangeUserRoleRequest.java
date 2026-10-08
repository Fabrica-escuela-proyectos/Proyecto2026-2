package com.codefactory.reservas_backend.identity.controller.dto;

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

    @NotBlank(message = "El rol es obligatorio")
    @Size(max = 30, message = "El rol no es válido")
    private String role;
}
