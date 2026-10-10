package com.codefactory.reservas_backend.identity.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Datos de presentación de un usuario para otros módulos (ADR-003), sin exponer la
 * entidad User. HU-22 guarda el nombre del cliente como copia en cada reserva para
 * que el proveedor lo vea en HU-24.
 */
public interface UserDirectoryService {

    /** Nombre completo del usuario, o vacío si no existe. */
    Optional<String> findFullName(UUID userId);
}
