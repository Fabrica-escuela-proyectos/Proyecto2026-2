package com.codefactory.reservas_backend.identidadAcceso.dto;

import com.codefactory.reservas_backend.identidadAcceso.validation.ValidPassword;
import com.codefactory.reservas_backend.identidadAcceso.validation.ValidPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
 
/**
 * Payload de POST /api/v1/users (HU-01). Deliberadamente NO tiene un campo
 * de rol: el rol "Cliente" se asigna siempre desde el servidor
 * (UserRegistrationService), nunca a partir de lo que envie el cliente HTTP.
 */
@Getter
@Setter
public class UserRegistrationRequest {
 
    @NotBlank(message = "El nombre completo es obligatorio")
    private String fullName;
 
    @NotBlank(message = "El correo electronico es obligatorio")
    @Email(message = "El formato del correo electronico no es valido")
    private String email;
 
    @NotBlank(message = "El numero de celular es obligatorio")
    @ValidPhone
    private String phoneNumber;
 
    @NotBlank(message = "La contrasena es obligatoria")
    @ValidPassword
    private String password;
}
