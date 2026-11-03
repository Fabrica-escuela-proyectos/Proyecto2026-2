package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;

import java.util.UUID;

/**
 * HU-11 - Desactivar un servicio. Mismo patrón que HU-16 ({@link ResourceDeactivationService}): se orquesta
 * aquí porque cuenta y cancela las reservas futuras en la misma transacción; el estado lo cambia Service
 * ({@code ServiceLifecycleService}).
 */
public interface ServiceDeactivationService {

    /** Motivo que ven el cliente y el proveedor en las reservas canceladas (AC: «servicio no disponible»). */
    String CANCELLATION_REASON = "Servicio no disponible: el proveedor lo desactivó";

    /**
     * @param confirm {@code true} si el proveedor acepta cancelar las reservas futuras afectadas
     * @throws com.codefactory.reservas_backend.reservation.domain.ConfirmationRequiredException hay reservas
     *         futuras y no se confirmó: no se cambia nada (409 con la cantidad afectada)
     */
    DeactivationResult deactivate(UUID serviceId, boolean confirm, UserIdentity requester, String originIp);

    record DeactivationResult(UUID serviceId, String name, boolean active, int cancelledBookings) {
    }
}
