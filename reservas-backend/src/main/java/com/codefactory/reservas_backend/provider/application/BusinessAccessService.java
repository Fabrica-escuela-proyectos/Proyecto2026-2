package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;

import java.util.UUID;

/**
 * Contrato que Provider expone a los demás módulos (ADR-003) para la regla de
 * pertenencia de HU-06: "el proveedor solo gestiona lo de su propio negocio".
 * Así Service, Resource y Reservation no importan las entidades Business ni
 * Provider ni sus repositorios.
 */
public interface BusinessAccessService {

    /**
     * Exige que el negocio exista y que {@code requester} sea el dueño.
     *
     * @throws com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException si no existe (404)
     * @throws org.springframework.security.access.AccessDeniedException si es de otro proveedor (403)
     */
    void requireOwner(UUID businessId, UserIdentity requester);
}
