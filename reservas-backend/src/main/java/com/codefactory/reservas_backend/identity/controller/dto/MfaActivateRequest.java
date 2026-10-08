package com.codefactory.reservas_backend.identity.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaActivateRequest {

    @NotBlank(message = "El código de verificación es obligatorio")
    @Size(max = 10, message = "El código de verificación no es válido")
    private String code;
}
