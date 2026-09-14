package com.codefactory.reservas_backend.identity.controller.dto;

import com.codefactory.reservas_backend.identity.controller.dto.validation.ValidPassword;
import com.codefactory.reservas_backend.identity.controller.dto.validation.ValidPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload de POST /api/v1/users (HU-01). Nombre y campos según
 * dtos-sprint-1.md sección 2 ("RegisterUserRequest"): fullName, email,
 * cellphone, password. Deliberadamente NO tiene un campo de rol: el rol
 * "Cliente" se asigna siempre desde el servidor (UserRegistrationService),
 * nunca a partir de lo que envíe el cliente HTTP.
 */
@Getter
@Setter
public class RegisterUserRequest {

    @NotBlank(message = "El nombre completo es obligatorio")
    private String fullName;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico no es válido")
    private String email;

    @NotBlank(message = "El número de celular es obligatorio")
    @ValidPhone
    private String cellphone;

    @NotBlank(message = "La contraseña es obligatoria")
    @ValidPassword
    private String password;
}
