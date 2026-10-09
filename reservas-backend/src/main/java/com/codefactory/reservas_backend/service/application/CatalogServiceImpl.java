package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.common.error.InvalidPaginationException;
import com.codefactory.reservas_backend.provider.application.BusinessDirectoryService;
import com.codefactory.reservas_backend.provider.application.BusinessInfo;
import com.codefactory.reservas_backend.provider.application.BusinessPage;
import com.codefactory.reservas_backend.service.controller.dto.CatalogDtos.BusinessDetailResponse;
import com.codefactory.reservas_backend.service.controller.dto.CatalogDtos.BusinessItem;
import com.codefactory.reservas_backend.service.controller.dto.CatalogDtos.BusinessPageResponse;
import com.codefactory.reservas_backend.service.controller.dto.CatalogDtos.ServiceItem;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {

    static final String NO_BUSINESSES_MESSAGE = "Aún no hay negocios registrados";
    static final String NO_SERVICES_MESSAGE = "Este negocio aún no tiene servicios disponibles";

    private final BusinessDirectoryService businessDirectoryService;
    private final ServiceOfferingRepository serviceRepository;

    @Override
    @Transactional(readOnly = true)
    public BusinessPageResponse listBusinesses(int page, int size) {
        if (page < 0) {
            throw new InvalidPaginationException("La página debe ser mayor o igual a 0");
        }
        if (size < 1) {
            throw new InvalidPaginationException("El tamaño de página debe ser mayor o igual a 1");
        }

        BusinessPage result = businessDirectoryService.list(page, Math.min(size, MAX_SIZE));
        List<BusinessItem> items = result.items().stream()
                .map(b -> new BusinessItem(b.id(), b.name()))
                .toList();
        // El mensaje solo aplica cuando el catálogo está realmente vacío, no cuando se pide una página fuera de rango.
        String message = result.totalElements() == 0 ? NO_BUSINESSES_MESSAGE : null;
        return new BusinessPageResponse(items, result.page(), result.size(), result.totalElements(),
                result.totalPages(), message);
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessDetailResponse getBusiness(UUID businessId) {
        BusinessInfo business = businessDirectoryService.get(businessId);
        List<ServiceItem> services = serviceRepository.findByBusinessIdAndActiveTrueOrderByNameAsc(businessId).stream()
                .map(s -> new ServiceItem(s.getId(), s.getName(), s.getDescription(), s.getDurationMinutes(), s.getPriceCop()))
                .toList();
        return new BusinessDetailResponse(business.id(), business.name(), services,
                services.isEmpty() ? NO_SERVICES_MESSAGE : null);
    }
}
