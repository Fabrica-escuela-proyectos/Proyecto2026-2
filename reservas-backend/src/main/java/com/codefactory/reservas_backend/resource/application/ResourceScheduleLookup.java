package com.codefactory.reservas_backend.resource.application;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lectura del horario de atención de los recursos para otros módulos (HU-20
 * calcula horarios libres; HU-22 valida que una reserva caiga dentro del horario).
 */
public interface ResourceScheduleLookup {

    /**
     * Rangos de atención de cada recurso en un día de la semana, ordenados por hora de
     * inicio. Un recurso sin rangos ese día (o sin horario) no aparece en el mapa.
     */
    Map<UUID, List<TimeWindow>> windowsOn(Collection<UUID> resourceIds, DayOfWeek day);
}
