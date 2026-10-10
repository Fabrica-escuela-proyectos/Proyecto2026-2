package com.codefactory.reservas_backend.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    // Límites de longitud (issue #9): una contraseña enorme llegaría a BCrypt
    // (costoso) y un correo enorme a la consulta; ninguna cuenta válida los supera.
    @Schema(description = "Correo con el que se registró", example = "ana.gomez@example.com")
    @NotBlank(message = "El correo electrónico es obligatorio")
    @Size(max = 150, message = "El correo electrónico no puede superar los 150 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 72, message = "La contraseña no puede superar los 72 caracteres")
    private String password;

    @Schema(description = "Código TOTP de 6 dígitos. Solo si la cuenta tiene MFA activa (obligatoria para administradores)", example = "123456")
    @Size(max = 10, message = "El código de verificación no es válido")
    private String mfaCode;
}
