package com.codefactory.reservas_backend.identity.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * dtos-sprint-1.md sección 3 ("LoginResponse"): token, expiresIn (segundos),
 * role. Se mantiene exactamente esta forma (sin campos adicionales de MFA)
 * para no romper el contrato documentado; el estado de MFA se consulta por
 * separado vía POST /api/v1/auth/mfa/setup, que es idempotente.
 */
@Getter
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private long expiresIn;
    private String role;
}
