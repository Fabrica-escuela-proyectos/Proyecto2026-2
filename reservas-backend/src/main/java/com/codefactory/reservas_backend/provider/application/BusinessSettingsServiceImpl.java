package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.controller.dto.BookingLeadTimeResponse;
import com.codefactory.reservas_backend.provider.domain.Business;
import com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessSettingsServiceImpl implements BusinessSettingsService {

    private final BusinessRepository businessRepository;
    private final BusinessAccessService businessAccessService;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public BookingLeadTimeResponse getBookingLeadTime(UUID businessId, UserIdentity requester) {
        businessAccessService.requireOwner(businessId, requester);
        return toResponse(find(businessId));
    }

    @Override
    @Transactional
    public BookingLeadTimeResponse updateBookingLeadTime(UUID businessId, int hours, UserIdentity requester, String originIp) {
        businessAccessService.requireOwner(businessId, requester);
        Business business = find(businessId);
        int previous = business.getMinAdvanceHours();
        business.setMinAdvanceHours(hours);
        businessRepository.save(business);

        auditService.registerEvent(AuditEventType.CONFIGURACION_NEGOCIO, requester.email(), "SUCCESS",
                "Antelación mínima del negocio " + businessId + ": " + previous + " h -> " + hours + " h", originIp);
        return toResponse(business);
    }

    @Override
    @Transactional(readOnly = true)
    public int minAdvanceHoursOf(UUID businessId) {
        return find(businessId).getMinAdvanceHours();
    }

    private Business find(UUID businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessNotFoundException("El negocio solicitado no existe"));
    }

    private BookingLeadTimeResponse toResponse(Business business) {
        return new BookingLeadTimeResponse(business.getId(), business.getMinAdvanceHours());
    }
}
