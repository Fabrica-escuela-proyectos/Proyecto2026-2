package com.codefactory.reservas_backend.identity.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Respuesta de POST /api/v1/auth/mfa/setup. Si {@code alreadyEnabled} es
 * true, {@code otpauthUri} viene null a propósito: el secreto ya activo no
 * se reexpone (evita que una llamada repetida a /setup filtre el secreto
 * vigente de alguien que ya completó su enrolamiento).
 */
@Getter
@AllArgsConstructor
public class MfaSetupResponse {
    private String otpauthUri;
    private boolean alreadyEnabled;
}
