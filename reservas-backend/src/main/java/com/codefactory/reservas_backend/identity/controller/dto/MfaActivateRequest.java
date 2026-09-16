package com.codefactory.reservas_backend.identity.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaActivateRequest {

    @NotBlank(message = "El código de verificación es obligatorio")
    private String code;
}
