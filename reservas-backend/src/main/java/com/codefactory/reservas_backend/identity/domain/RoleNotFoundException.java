package com.codefactory.reservas_backend.identity.domain;

/**
 * HU-05, escenario "Administrador intenta asignar un rol inexistente": el
 * valor de "role" recibido en ChangeUserRoleRequest no corresponde a ningún
 * RoleName conocido. Mapea a 400 con un "error uniforme" (el propio Gherkin
 * lo pide así), no a 404 — el problema es la solicitud, no un recurso con id.
 */
public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(String message) {
        super(message);
    }
}
