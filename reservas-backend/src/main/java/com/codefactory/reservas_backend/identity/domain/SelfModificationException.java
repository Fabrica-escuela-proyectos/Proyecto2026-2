package com.codefactory.reservas_backend.identity.domain;

/**
 * HU-05, escenario "Usuario intenta modificar sus propios permisos": ningún
 * usuario —ni siquiera un Administrador— puede cambiar su propio rol o
 * eliminar su propia cuenta a través de los endpoints de gestión de HU-05.
 */
public class SelfModificationException extends RuntimeException {
    public SelfModificationException(String message) {
        super(message);
    }
}
