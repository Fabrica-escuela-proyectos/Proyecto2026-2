package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.service.controller.dto.CatalogDtos.BusinessDetailResponse;
import com.codefactory.reservas_backend.service.controller.dto.CatalogDtos.BusinessPageResponse;

import java.util.UUID;

/** HU-13 - Catálogo de negocios y servicios para usuarios autenticados (solo lectura). */
public interface CatalogService {

    /** Tamaño de página por defecto y tope máximo (un tamaño mayor se recorta al tope). */
    int DEFAULT_SIZE = 20;
    int MAX_SIZE = 50;

    /**
     * @param page página desde 0
     * @param size elementos por página; se recorta a {@link #MAX_SIZE}
     * @throws com.codefactory.reservas_backend.common.error.InvalidPaginationException si page &lt; 0 o size &lt; 1
     */
    BusinessPageResponse listBusinesses(int page, int size);

    /** @throws com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException si no existe */
    BusinessDetailResponse getBusiness(UUID businessId);
}
