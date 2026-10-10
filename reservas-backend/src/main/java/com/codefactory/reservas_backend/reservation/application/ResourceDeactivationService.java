package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;

import java.util.UUID;

/**
 * HU-16 - Desactivar un recurso. Se orquesta en el módulo Reservation porque debe contar y cancelar
 * las reservas futuras en la misma transacción; el cambio de estado del recurso lo hace Resource
 * ({@code ResourceLifecycleService}).
 */
public interface ResourceDeactivationService {

    String CANCELLATION_REASON = "El recurso fue desactivado por el proveedor";

    /**
     * @param confirm {@code true} si el proveedor acepta cancelar las reservas futuras afectadas
     * @return el resultado: recurso inactivo y cuántas reservas se cancelaron (0 si ya estaba inactivo)
     * @throws com.codefactory.reservas_backend.reservation.domain.ConfirmationRequiredException hay reservas
     *         futuras y no se confirmó: no se cambia nada (409 con la cantidad afectada)
     * @throws com.codefactory.reservas_backend.resource.domain.ResourceNotFoundException el recurso no existe (404)
     * @throws org.springframework.security.access.AccessDeniedException el recurso es de otro proveedor (403)
     */
    DeactivationResult deactivate(UUID resourceId, boolean confirm, UserIdentity requester, String originIp);

    record DeactivationResult(UUID resourceId, String name, boolean active, int cancelledBookings) {
    }
}
