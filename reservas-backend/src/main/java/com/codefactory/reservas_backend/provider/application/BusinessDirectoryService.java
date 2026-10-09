package com.codefactory.reservas_backend.provider.application;

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
}
