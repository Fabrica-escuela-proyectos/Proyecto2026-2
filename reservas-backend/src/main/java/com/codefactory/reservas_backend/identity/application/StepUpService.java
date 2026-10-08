package com.codefactory.reservas_backend.identity.application;

/**
 * Confirmación adicional (step-up) para operaciones sensibles de un
 * administrador (HU-02, escenario "Verificación adicional para operaciones
 * sensibles"; ADR-004, decisión P6): además del token, la operación exige un
 * código TOTP vigente en el header {@code X-MFA-Code}.
 */
public interface StepUpService {

    /** Header HTTP donde el administrador envía su código MFA en operaciones sensibles. */
    String MFA_CODE_HEADER = "X-MFA-Code";

    /**
     * Exige un código MFA válido de {@code actor}.
     *
     * @throws com.codefactory.reservas_backend.identity.domain.MfaEnrollmentRequiredException si el administrador no tiene MFA activa (403)
     * @throws com.codefactory.reservas_backend.identity.domain.MfaRequiredException si falta el código (401 MFA_REQUIRED)
     * @throws com.codefactory.reservas_backend.identity.domain.InvalidCredentialsException si el código es incorrecto (401)
     * @throws com.codefactory.reservas_backend.identity.infrastructure.TooManyRequestsException si hubo demasiados fallos seguidos (429)
     */
    void requireValidCode(UserIdentity actor, String mfaCode, String operation, String originIp);
}
