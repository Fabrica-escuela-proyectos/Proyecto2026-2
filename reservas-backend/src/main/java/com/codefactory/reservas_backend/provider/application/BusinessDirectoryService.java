package com.codefactory.reservas_backend.provider.application;

import java.util.List;
import java.util.UUID;

/**
 * Consulta de negocios para otros módulos (catálogo de HU-13, y más adelante
 * HU-20/22). Todo negocio pertenece a un proveedor registrado, así que no hace
 * falta filtrar por eso.
 */
public interface BusinessDirectoryService {

    /** Página de negocios ordenada por nombre (sin distinguir mayúsculas) y luego por id. */
    BusinessPage list(int page, int size);

    /** @throws com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException si no existe */
    BusinessInfo get(UUID businessId);

    /**
     * {@code true} si el negocio existe y la cuenta de su proveedor está habilitada
     * (HU-20: el servicio de un proveedor inactivo no está disponible).
     */
    boolean isOwnerEnabled(UUID businessId);

    /** Ids de los negocios del proveedor cuyo usuario es {@code userId}; vacío si no es proveedor (HU-28). */
    List<UUID> businessIdsOfUser(UUID userId);
}
