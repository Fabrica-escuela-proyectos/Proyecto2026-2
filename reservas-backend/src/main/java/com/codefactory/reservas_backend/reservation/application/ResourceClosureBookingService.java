package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.resource.application.ClosureInfo;

import java.util.UUID;

/**
 * HU-21 - Bloquear un horario puntual. Se orquesta en Reservation (como HU-16) porque un cierre puede
 * cancelar reservas y eso debe pasar en la misma transacción que el registro del cierre.
 */
public interface ResourceClosureBookingService {

    String CANCELLATION_REASON = "El recurso no estará disponible en ese horario (cierre programado por el proveedor)";

    /**
     * @param confirm {@code true} si el proveedor acepta cancelar las reservas que caen en el cierre
     * @throws com.codefactory.reservas_backend.reservation.domain.ConfirmationRequiredException hay reservas
     *         afectadas y no se confirmó: no se cambia nada (409 con la cantidad)
     */
    ClosureResult close(UUID resourceId, String date, String start, String end, String reason, boolean confirm,
                        UserIdentity requester, String originIp);

    record ClosureResult(ClosureInfo closure, int cancelledBookings, String message) {
    }
}
