package com.codefactory.reservas_backend.e2e.models;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public final class Fechas {

    private static final DateTimeFormatter DIA_MES_ANIO =
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private Fechas() {
    }

    // los criterios escriben las fechas como 3/11/2026; una fecha que no existe se envia tal cual para que la API la rechace
    public static String enLaApi(String fecha) {
        try {
            return LocalDate.parse(fecha, DIA_MES_ANIO).toString();
        } catch (DateTimeParseException e) {
            return fecha;
        }
    }

    public static LocalDate dia(String fecha) {
        return LocalDate.parse(enLaApi(fecha));
    }
}
