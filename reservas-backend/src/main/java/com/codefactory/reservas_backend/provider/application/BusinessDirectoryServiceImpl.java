package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.identity.application.AccountStatusService;
import com.codefactory.reservas_backend.provider.domain.Business;
import com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import com.codefactory.reservas_backend.provider.infrastructure.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessDirectoryServiceImpl implements BusinessDirectoryService {

    private final BusinessRepository businessRepository;
    private final ProviderRepository providerRepository;
    private final AccountStatusService accountStatusService;

    @Override
    @Transactional(readOnly = true)
    public BusinessPage list(int page, int size) {
        // Orden estable: por nombre sin distinguir mayúsculas y, a igualdad, por id.
        Sort sort = Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id"));
        Page<Business> result = businessRepository.findAll(PageRequest.of(page, size, sort));
        return new BusinessPage(
                result.getContent().stream().map(BusinessDirectoryServiceImpl::toInfo).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessInfo get(UUID businessId) {
        return businessRepository.findById(businessId)
                .map(BusinessDirectoryServiceImpl::toInfo)
                .orElseThrow(() -> new BusinessNotFoundException("El negocio solicitado no existe"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwnerEnabled(UUID businessId) {
        return businessRepository.findById(businessId)
                .flatMap(business -> providerRepository.findById(business.getProviderId()))
                .map(provider -> accountStatusService.isEnabled(provider.getUserId()))
                .orElse(false);
    }

    private static BusinessInfo toInfo(Business business) {
        return new BusinessInfo(business.getId(), business.getName());
    }
}
