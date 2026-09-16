package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.identity.controller.dto.MfaSetupResponse;

import java.util.UUID;

/**
 * MFA (TOTP) para cuentas administrativas — ADR-002 sección 8, HU-02
 * ("Verificación adicional para cuentas administrativas") y HU-05 ("se
 * activa un evento obligatorio, para la creación de MFA para el nuevo
 * Administrador").
 */
public interface MfaService {

    /**
     * Idempotente: crea el secreto pendiente si el usuario no tiene uno
     * todavía y lo devuelve como otpauth URI; si ya está activo, no
     * reexpone el secreto (ver MfaSetupResponse).
     */
    MfaSetupResponse setup(UUID userId);

    /**
     * Confirma el enrolamiento: valida el código contra el secreto
     * pendiente y, si es correcto, marca la MFA como activa.
     */
    void activate(UUID userId, String code);

    boolean isEnabled(UUID userId);

    /**
     * Usado por el login (HU-02): true solo si el usuario tiene MFA activa
     * y el código recibido es válido para su secreto.
     */
    boolean verifyCode(UUID userId, String code);

    /**
     * HU-05: al ascender un usuario a ADMINISTRADOR, crea (si no existe ya)
     * una configuración de MFA pendiente para ese usuario y audita el
     * evento obligatorio. No activa la MFA por sí sola — el usuario debe
     * completar /mfa/setup + /mfa/activate.
     */
    void triggerMandatorySetup(UUID userId);
}
