package com.codefactory.reservas_backend.e2e.models;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Datos del formulario de registro. Cada llamada a validos() genera correo y celular únicos. */
public record DatosDeRegistro(String nombreCompleto, String correo, String celular, String contrasena,
                              String nombreDelNegocio) {

    public static final String CONTRASENA_VALIDA = "Segura#2026";

    // Celulares colombianos (10 dígitos, empiezan por 3) en un rango propio de estas pruebas.
    private static final AtomicLong CELULARES = new AtomicLong(3_400_000_000L);

    public static String nuevoCelular() {
        return String.valueOf(CELULARES.incrementAndGet());
    }

    public static String nuevoCorreo(String prefijo) {
        return prefijo + "." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    public static DatosDeRegistro validos() {
        return new DatosDeRegistro("Usuario de Prueba", nuevoCorreo("usuario"), nuevoCelular(),
                CONTRASENA_VALIDA, null);
    }

    public DatosDeRegistro conCorreo(String valor) {
        return new DatosDeRegistro(nombreCompleto, valor, celular, contrasena, nombreDelNegocio);
    }

    public DatosDeRegistro conCelular(String valor) {
        return new DatosDeRegistro(nombreCompleto, correo, valor, contrasena, nombreDelNegocio);
    }

    public DatosDeRegistro conContrasena(String valor) {
        return new DatosDeRegistro(nombreCompleto, correo, celular, valor, nombreDelNegocio);
    }

    public DatosDeRegistro comoProveedor() {
        return new DatosDeRegistro(nombreCompleto, correo, celular, contrasena,
                "Negocio " + UUID.randomUUID().toString().substring(0, 6));
    }

    /** Deja vacío el campo indicado con el nombre que usan los escenarios. */
    public DatosDeRegistro sinElCampo(String campo) {
        return switch (campo) {
            case "full name" -> new DatosDeRegistro("", correo, celular, contrasena, nombreDelNegocio);
            case "email" -> conCorreo("");
            case "cell phone number" -> conCelular("");
            case "password" -> conContrasena("");
            case "business name" -> new DatosDeRegistro(nombreCompleto, correo, celular, contrasena, "");
            default -> throw new IllegalArgumentException("Campo desconocido: " + campo);
        };
    }

    /** Nombre del campo en la respuesta de error de la API. */
    public static String claveEnLaRespuesta(String campo) {
        return switch (campo) {
            case "full name" -> "fullName";
            case "email" -> "email";
            case "cell phone number" -> "cellphone";
            case "password" -> "password";
            case "business name" -> "businessName";
            default -> throw new IllegalArgumentException("Campo desconocido: " + campo);
        };
    }

    public Map<String, String> comoCuerpo() {
        Map<String, String> cuerpo = new LinkedHashMap<>();
        cuerpo.put("fullName", nombreCompleto);
        cuerpo.put("email", correo);
        cuerpo.put("cellphone", celular);
        cuerpo.put("password", contrasena);
        if (nombreDelNegocio != null) {
            cuerpo.put("businessName", nombreDelNegocio);
        }
        return cuerpo;
    }
}
