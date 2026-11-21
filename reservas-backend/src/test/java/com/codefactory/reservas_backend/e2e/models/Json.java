package com.codefactory.reservas_backend.e2e.models;

import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/** Convierte un mapa en JSON con el Jackson del proyecto (Spring Boot 4 usa Jackson 3). */
public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {
    }

    public static String de(Map<String, String> cuerpo) {
        return MAPPER.writeValueAsString(cuerpo);
    }
}
