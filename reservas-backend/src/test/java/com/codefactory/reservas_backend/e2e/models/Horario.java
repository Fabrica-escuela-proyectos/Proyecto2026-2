package com.codefactory.reservas_backend.e2e.models;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Fecha y hora de inicio de una reserva, en hora de Bogotá, como las recibe y devuelve la API. */
public record Horario(LocalDate fecha, LocalTime inicio) {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public String fechaEnLaApi() {
        return fecha.toString();
    }

    public String inicioEnLaApi() {
        return inicio.format(HORA);
    }

    public String finEnLaApi(int duracionEnMinutos) {
        return inicio.plusMinutes(duracionEnMinutos).format(HORA);
    }
}
