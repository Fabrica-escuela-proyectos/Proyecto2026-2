package com.codefactory.reservas_backend.provider.controller.dto;

import com.codefactory.reservas_backend.common.validation.ValidPassword;
import com.codefactory.reservas_backend.common.validation.ValidPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload de POST /api/v1/providers (HU-03). Campos según
 * dtos-sprint-1.md sección 4 ("RegisterProviderRequest"): fullName, email,
 * cellphone, password, businessName. Deliberadamente NO tiene un campo de
 * rol (mismo motivo que RegisterUserRequest en HU-01): el rol "Proveedor"
 * se asigna siempre desde el servidor. Esto por sí solo cubre el escenario
 * "El proveedor no puede autoasignarse un rol privilegiado durante el
 * registro" — un "role" que el cliente envíe en el JSON simplemente no
 * tiene dónde aterrizar en este DTO.
 */
@Getter
@Setter
public class RegisterProviderRequest {

    // Límites de longitud (issue #9): ver RegisterUserRequest. businesses.name
    // es VARCHAR(150) (V3).
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

    @NotBlank(message = "El nombre del negocio es obligatorio")
    @Size(max = 150, message = "El nombre del negocio no puede superar los 150 caracteres")
    private String businessName;
}
