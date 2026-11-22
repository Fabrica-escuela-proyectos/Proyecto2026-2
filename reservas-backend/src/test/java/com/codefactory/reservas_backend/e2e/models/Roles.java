package com.codefactory.reservas_backend.e2e.models;

import java.util.Locale;

/**
 * Los escenarios nombran los roles como los muestra la API ("Cliente", "Proveedor", "Administrador"), que es también
 * como los citan sus mensajes. Para consultar la base o enviarlos a la API se pasan a mayúsculas (CLIENTE, ...).
 */
public final class Roles {

    private Roles() {
    }

    /** Nombre del rol como lo guarda y lo devuelve la API (también sirve para un rol inexistente como "Supervisor"). */
    public static String nombreEnLaApi(String rol) {
        return rol.toUpperCase(Locale.ROOT);
    }

    /** Cómo nombran el rol los mensajes de la API: tal cual aparece en el escenario ("Proveedor"). */
    public static String etiquetaEnLaApi(String rol) {
        return rol;
    }
}
