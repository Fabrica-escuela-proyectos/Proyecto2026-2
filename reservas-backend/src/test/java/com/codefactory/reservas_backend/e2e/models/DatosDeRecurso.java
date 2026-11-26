package com.codefactory.reservas_backend.e2e.models;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Datos que el proveedor envía al registrar (HU-14) o editar (HU-15) un recurso. Un campo nulo no se envía
 * (para probar "sin nombre" o "sin tipo"); uno vacío se envía vacío. {@code idDelNegocioEnElCuerpo} sirve para
 * probar que la API ignora un negocio indicado en el cuerpo: el que manda es el de la ruta (o el del recurso).
 */
public record DatosDeRecurso(String nombre, String tipo, String idDelNegocioEnElCuerpo) {

    public static DatosDeRecurso de(String nombre, String tipo) {
        return new DatosDeRecurso(nombre, tipo, null);
    }

    public DatosDeRecurso conNegocioEnElCuerpo(String idDelNegocio) {
        return new DatosDeRecurso(nombre, tipo, idDelNegocio);
    }

    public Map<String, Object> comoCuerpo() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        if (nombre != null) {
            cuerpo.put("name", nombre);
        }
        if (tipo != null) {
            cuerpo.put("type", tipo);
        }
        if (idDelNegocioEnElCuerpo != null) {
            cuerpo.put("businessId", idDelNegocioEnElCuerpo);
        }
        return cuerpo;
    }
}
