package com.codefactory.reservas_backend.identity.application;

import java.util.UUID;

/**
 * Estado de una cuenta de usuario para otros módulos (ADR-003), sin exponer la
 * entidad User. HU-20: el servicio de un proveedor con la cuenta inactiva no
 * debe mostrarse como disponible.
 */
public interface AccountStatusService {

    /** {@code true} solo si la cuenta existe y está habilitada. */
    boolean isEnabled(UUID userId);
}
