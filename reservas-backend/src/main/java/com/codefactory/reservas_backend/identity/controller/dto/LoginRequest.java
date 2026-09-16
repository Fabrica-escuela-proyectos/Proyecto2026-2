package com.codefactory.reservas_backend.identity.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload de POST /api/v1/auth/login (HU-02). Campos según
 * dtos-sprint-1.md sección 3 ("LoginRequest"): email, password.
 *
 * {@code mfaCode} no está en el documento original porque HU-02 no había
 * definido todavía el mecanismo concreto de "verificación adicional para
 * cuentas administrativas" (su propio escenario Gherkin). Se agrega como
 * campo opcional: los usuarios sin MFA activo (todo Cliente/Proveedor, y un
 * Administrador que aún no completó /mfa/activate) simplemente no lo envían.
 */
@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "El correo electrónico es obligatorio")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    private String mfaCode;
}
