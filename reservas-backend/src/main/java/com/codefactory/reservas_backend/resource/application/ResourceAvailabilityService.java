package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.AvailabilityResponse;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.TimeRange;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.WeekScheduleRequest;

import java.util.List;
import java.util.UUID;

/**
 * HU-19 - Horarios de atención (disponibilidad semanal) de un recurso. Solo el
 * proveedor dueño del negocio del recurso. Toda operación de escritura es
 * atómica: si el horario es inválido, el recurso conserva el anterior.
 */
public interface ResourceAvailabilityService {

    /** Zona horaria de todos los horarios (negocios de Colombia). */
    String TIMEZONE = "America/Bogota";

    /** Reemplaza TODA la semana; los días no incluidos quedan no disponibles. */
    AvailabilityResponse replaceWeek(UUID resourceId, WeekScheduleRequest request, UserIdentity requester, String originIp);

    /** Reemplaza solo un día (1 = lunes ... 7 = domingo); los demás días y recursos no cambian. Lista vacía = no disponible. */
    AvailabilityResponse replaceDay(UUID resourceId, int dayOfWeek, List<TimeRange> ranges, UserIdentity requester, String originIp);

    AvailabilityResponse get(UUID resourceId, UserIdentity requester);
}
