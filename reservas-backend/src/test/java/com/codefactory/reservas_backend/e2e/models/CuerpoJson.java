package com.codefactory.reservas_backend.e2e.models;

import tools.jackson.databind.json.JsonMapper;

/** Convierte en JSON cuerpos con números, listas u objetos anidados (Json.de solo admite textos). */
public final class CuerpoJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private CuerpoJson() {
    }

    public static String de(Object cuerpo) {
        return MAPPER.writeValueAsString(cuerpo);
    }
}
