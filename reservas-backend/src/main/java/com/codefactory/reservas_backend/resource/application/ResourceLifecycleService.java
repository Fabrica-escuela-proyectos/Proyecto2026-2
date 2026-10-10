package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;

import java.util.UUID;

/**
 * Ciclo de vida de un recurso: desactivar (HU-16) y reactivar (HU-17). La desactivación con reservas
 * futuras la orquesta el módulo Reservation, que cancela las reservas y luego llama a
 * {@link #deactivate}; así Resource no depende de Reservation.
 */
public interface ResourceLifecycleService {

    /**
     * Carga el recurso con bloqueo de fila (se mantiene hasta el fin de la transacción) y verifica
     * que el solicitante es el dueño del negocio. Serializa desactivaciones y reservas simultáneas.
     *
     * @throws com.codefactory.reservas_backend.resource.domain.ResourceNotFoundException si no existe (404)
     * @throws org.springframework.security.access.AccessDeniedException si es de otro proveedor (403)
     */
    ResourceInfo lockOwnedResource(UUID resourceId, UserIdentity requester);

    /** Marca el recurso como inactivo y lo audita. Debe llamarse tras {@link #lockOwnedResource} en la misma transacción. */
    void deactivate(UUID resourceId, UserIdentity requester, int cancelledBookings, String originIp);

    /**
     * HU-17 - Reactiva el MISMO recurso (no se duplica). Idempotente: si ya estaba activo no cambia nada.
     * Las reservas canceladas por la desactivación siguen canceladas.
     */
    ResourceInfo reactivate(UUID resourceId, UserIdentity requester, String originIp);
}
