package com.codefactory.reservas_backend.resource.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Contratos del horario de atención de un recurso (HU-19). Días ISO-8601
 * (1 = lunes ... 7 = domingo); horas "HH:mm" en hora local del negocio
 * (America/Bogota). Un día sin rangos queda no disponible.
 */
public final class AvailabilityDtos {

    public static final String TIME_FORMAT_MESSAGE = "El rango horario no es válido: use el formato HH:mm (00:00 a 23:59)";
    static final String TIME_REGEX = "^([01]\\d|2[0-3]):[0-5]\\d$";
    public static final int MAX_RANGES_PER_DAY = 10;

    private AvailabilityDtos() {
    }

    /** Rango [start, end) de un día, en ambos sentidos (petición y respuesta). */
    public record TimeRange(
            @NotBlank(message = TIME_FORMAT_MESSAGE) @Pattern(regexp = TIME_REGEX, message = TIME_FORMAT_MESSAGE) String start,
            @NotBlank(message = TIME_FORMAT_MESSAGE) @Pattern(regexp = TIME_REGEX, message = TIME_FORMAT_MESSAGE) String end) {
    }

    /** Cuerpo de PUT .../availability/{dayOfWeek}: reemplaza solo ese día; lista vacía = no disponible. */
    public record DayRangesRequest(
            @NotNull(message = "La lista de rangos es obligatoria (puede ir vacía)")
            @Size(max = MAX_RANGES_PER_DAY, message = "Un día admite como máximo 10 rangos horarios")
            List<@Valid @NotNull(message = "Un rango horario no puede ser nulo") TimeRange> ranges) {
    }

    /** Un día dentro del PUT de la semana completa. */
    public record DayScheduleRequest(
            @NotNull(message = "El día de la semana es obligatorio")
            @Min(value = 1, message = "El día debe estar entre 1 (lunes) y 7 (domingo)")
            @Max(value = 7, message = "El día debe estar entre 1 (lunes) y 7 (domingo)")
            Integer dayOfWeek,
            @NotNull(message = "La lista de rangos es obligatoria (puede ir vacía)")
            @Size(max = MAX_RANGES_PER_DAY, message = "Un día admite como máximo 10 rangos horarios")
            List<@Valid @NotNull(message = "Un rango horario no puede ser nulo") TimeRange> ranges) {
    }

    /** Cuerpo de PUT .../availability: reemplaza TODA la semana; los días omitidos quedan no disponibles. */
    public record WeekScheduleRequest(
            @NotNull(message = "La lista de días es obligatoria (puede ir vacía)")
            @Size(max = 7, message = "La semana tiene como máximo 7 días")
            List<@Valid @NotNull(message = "Un día no puede ser nulo") DayScheduleRequest> days) {
    }

    /** Un día del horario vigente; {@code ranges} vacío = no disponible. */
    public record DaySchedule(int dayOfWeek, List<TimeRange> ranges) {
    }

    /** Horario semanal vigente: siempre trae los 7 días. */
    public record AvailabilityResponse(UUID resourceId, String timezone, List<DaySchedule> days) {
    }
}
