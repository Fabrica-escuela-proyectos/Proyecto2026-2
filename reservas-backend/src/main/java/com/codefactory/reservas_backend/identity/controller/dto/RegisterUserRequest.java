package com.codefactory.reservas_backend.identity.controller.dto;

import com.codefactory.reservas_backend.common.validation.ValidPassword;
import com.codefactory.reservas_backend.common.validation.ValidPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    // Límites de longitud (issue #9): coinciden con las columnas de V1
    // (full_name y email son VARCHAR(150)) y con BCrypt, que solo usa los
    // primeros 72 bytes de la contraseña.
    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(max = 150, message = "El nombre completo no puede superar los 150 caracteres")
    private String fullName;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico no es válido")
    @Size(max = 150, message = "El correo electrónico no puede superar los 150 caracteres")
    private String email;

    @NotBlank(message = "El número de celular es obligatorio")
    @ValidPhone
    private String cellphone;

    @NotBlank(message = "La contraseña es obligatoria")
    @ValidPassword
    @Size(max = 72, message = "La contraseña no puede superar los 72 caracteres")
    private String password;
}
