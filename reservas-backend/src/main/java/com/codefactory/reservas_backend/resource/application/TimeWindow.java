package com.codefactory.reservas_backend.resource.application;

import java.time.LocalTime;

/** Rango horario [start, end) de atención de un recurso en un día, en hora local del negocio. */
public record TimeWindow(LocalTime start, LocalTime end) {
}
