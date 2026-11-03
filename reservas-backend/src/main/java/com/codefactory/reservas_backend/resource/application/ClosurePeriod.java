package com.codefactory.reservas_backend.resource.application;

import java.time.Instant;

/** Periodo de un cierre ya validado: [start, end) como instantes. */
public record ClosurePeriod(Instant start, Instant end) {
}
