package com.codefactory.reservas_backend.e2e.models;

import java.util.LinkedHashMap;
import java.util.Map;

/** Correo, contraseña y (solo administradores) código de verificación adicional. */
public record Credenciales(String correo, String contrasena, String codigoMfa) {

    public Credenciales conCodigo(String codigo) {
        return new Credenciales(correo, contrasena, codigo);
    }

    public Map<String, String> comoCuerpo() {
        Map<String, String> cuerpo = new LinkedHashMap<>();
        cuerpo.put("email", correo);
        cuerpo.put("password", contrasena);
        if (codigoMfa != null) {
            cuerpo.put("mfaCode", codigoMfa);
        }
        return cuerpo;
    }
}
