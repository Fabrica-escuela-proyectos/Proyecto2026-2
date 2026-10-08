package com.codefactory.reservas_backend.identity.domain;

/**
 * Un administrador sin MFA activa intentó usar algo distinto de
 * {@code /api/v1/auth/mfa/**} y el cierre de sesión (ADR-004, decisión P4).
 * Se responde {@code 403 MFA_ENROLLMENT_REQUIRED}: puede iniciar sesión, pero
 * hasta completar el enrolamiento (setup + activate) no puede administrar.
 * Así "MFA obligatorio" deja de ser solo un evento de auditoría (HU-05) sin
 * bloquear la cuenta de forma permanente.
 */
public class MfaEnrollmentRequiredException extends RuntimeException {

    public static final String DEFAULT_MESSAGE =
            "Debe configurar la verificación en dos pasos (MFA) antes de continuar: "
                    + "POST /api/v1/auth/mfa/setup y POST /api/v1/auth/mfa/activate";

    public MfaEnrollmentRequiredException() {
        super(DEFAULT_MESSAGE);
    }
}
