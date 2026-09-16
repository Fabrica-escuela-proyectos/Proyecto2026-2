package com.codefactory.reservas_backend.identity.domain;

/**
 * Cubre HU-02, escenario "Intento de inicio de sesión con credenciales
 * inválidas": credenciales incorrectas, cuenta deshabilitada, o código MFA
 * faltante/incorrecto para una cuenta administrativa (escenario
 * "Verificación adicional para cuentas administrativas"). Se usa el mismo
 * tipo y el mismo mensaje genérico para los tres casos a propósito: el
 * sistema nunca debe revelar cuál de las validaciones falló
 * (errores-api-sprint-1.md sección 5: "no indicar si el correo existe o si
 * específicamente la contraseña fue incorrecta").
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
