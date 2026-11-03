package com.codefactory.reservas_backend.resource.application;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lectura del horario de atención de los recursos para otros módulos (HU-20
 * calcula horarios libres; HU-22 valida que una reserva caiga dentro del horario;
 * los reportes del Sprint 3 calculan la ocupación).
 */
public interface ResourceScheduleLookup {

    /**
     * Rangos de atención de cada recurso en un día de la semana, ordenados por hora de
     * inicio. Un recurso sin rangos ese día (o sin horario) no aparece en el mapa.
     * No descuenta los cierres puntuales: para una fecha concreta usar {@link #effectiveWindowsOn}.
     */
    Map<UUID, List<TimeWindow>> windowsOn(Collection<UUID> resourceIds, DayOfWeek day);

    /**
     * Horario EFECTIVO de una fecha: el semanal (HU-19) menos los cierres puntuales (HU-21) que la tocan.
     * Un recurso cerrado todo el día, o sin horario ese día, no aparece en el mapa.
     */
    Map<UUID, List<TimeWindow>> effectiveWindowsOn(Collection<UUID> resourceIds, LocalDate date);

    /** HU-21/HU-22: ¿algún cierre del recurso se cruza con [start, end)? Consulta directa a la base. */
    boolean isClosed(UUID resourceId, Instant start, Instant end);

    /**
     * Minutos de atención efectivos (horario semanal menos cierres) de cada recurso y cada día de
     * [from, to], ambos inclusive. Los días sin atención no aparecen. Base del cálculo de ocupación.
     */
    Map<UUID, Map<LocalDate, Long>> availableMinutesByDay(Collection<UUID> resourceIds, LocalDate from, LocalDate to);
}
