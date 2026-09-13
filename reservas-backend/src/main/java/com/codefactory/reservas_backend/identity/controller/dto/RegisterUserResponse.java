package com.codefactory.reservas_backend.identity.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * dtos-sprint-1.md tabla 9 solo dice "Respuesta de registro" para HU01, sin
 * un nombre de clase específico; se nombró RegisterUserResponse por
 * consistencia con RegisterUserRequest. Campos según
 * api-contract-POST-users.md.
 */
@Getter
@AllArgsConstructor
public class RegisterUserResponse {
    private UUID id;
    private String fullName;
    private String email;
    private String role;
}
