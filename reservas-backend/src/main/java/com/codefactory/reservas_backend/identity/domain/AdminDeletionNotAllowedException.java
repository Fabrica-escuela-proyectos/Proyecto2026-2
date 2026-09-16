package com.codefactory.reservas_backend.identity.domain;

/**
 * HU-05, escenario "administrador elimina usuario con rol": el Gherkin solo
 * habilita eliminar usuarios con rol "Cliente" o "Proveedor" explícitamente;
 * se interpreta como una exclusión deliberada de cuentas ADMINISTRADOR de
 * este flujo de borrado (evita que un admin se quede sin pares o bloquee la
 * plataforma por accidente). Ver docs/matriz-actualizaciones.md.
 */
public class AdminDeletionNotAllowedException extends RuntimeException {
    public AdminDeletionNotAllowedException(String message) {
        super(message);
    }
}
