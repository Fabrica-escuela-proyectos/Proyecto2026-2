package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;

import java.util.UUID;

/**
 * Ciclo de vida de un servicio (HU-11 desactivar, HU-12 reactivar). Igual que con los recursos (HU-16):
 * la desactivación con reservas futuras la orquesta el módulo de reservas, que es el que puede
 * cancelarlas; aquí solo se bloquea, se valida la pertenencia y se cambia el estado (ADR-007).
 */
public interface ServiceLifecycleService {

    /** Bloquea el servicio (FOR UPDATE) y exige que sea del proveedor; 404 si no existe, 403 si es ajeno. */
    ServiceInfo lockOwnedService(UUID serviceId, UserIdentity requester);

    void deactivate(UUID serviceId, UserIdentity requester, int cancelledBookings, String originIp);

    /** Idempotente: reactivar un servicio activo no cambia nada. */
    ServiceInfo reactivate(UUID serviceId, UserIdentity requester, String originIp);
}
