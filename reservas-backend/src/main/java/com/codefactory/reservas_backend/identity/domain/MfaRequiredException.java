package com.codefactory.reservas_backend.identity.domain;

/**
 * La operación exige un código MFA que no llegó. Se lanza en dos lugares
 * (ADR-004, decisiones P5 y P6): en el login de un administrador con MFA
 * activa cuya contraseña es válida pero no envió {@code mfaCode}, y en una
 * operación sensible sin el header {@code X-MFA-Code}. Se responde
 * {@code 401 MFA_REQUIRED} para que el cliente sepa que debe pedir el código;
 * un código incorrecto, en cambio, da el 401 genérico de credenciales
 * inválidas (no se revela cuál validación falló).
 */
public class MfaRequiredException extends RuntimeException {
    public MfaRequiredException(String message) {
        super(message);
    }
}
