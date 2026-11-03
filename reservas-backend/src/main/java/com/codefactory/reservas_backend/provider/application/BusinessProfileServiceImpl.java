package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.controller.dto.UpdateBusinessRequest;
import com.codefactory.reservas_backend.provider.domain.Business;
import com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException;
import com.codefactory.reservas_backend.provider.domain.BusinessType;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class BusinessProfileServiceImpl implements BusinessProfileService {

    private final BusinessRepository businessRepository;
    private final BusinessAccessService businessAccessService;
    private final AuditService auditService;

    @Override
    @Transactional
    public BusinessProfile update(UUID businessId, UpdateBusinessRequest request, UserIdentity requester, String originIp) {
        businessAccessService.requireOwner(businessId, requester);
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessNotFoundException("El negocio solicitado no existe"));

        // Solo se toca lo que llegó (null = no cambia); cada cambio queda nombrado en la auditoría.
        List<String> changes = new ArrayList<>();
        if (request.getName() != null) {
            apply("nombre", request.getName().trim(), business.getName(), business::setName, changes);
        }
        if (request.getDescription() != null) {
            apply("descripción", blankToNull(request.getDescription()), business.getDescription(), business::setDescription, changes);
        }
        if (request.getAddress() != null) {
            apply("dirección", blankToNull(request.getAddress()), business.getAddress(), business::setAddress, changes);
        }
        if (request.getPhone() != null) {
            apply("teléfono", blankToNull(request.getPhone()), business.getPhone(), business::setPhone, changes);
        }
        if (request.getBusinessType() != null) {
            String type = blankToNull(request.getBusinessType());
            BusinessType value = type == null ? null : BusinessType.valueOf(type.toUpperCase(Locale.ROOT));
            if (value != business.getBusinessType()) {
                changes.add("tipo");
                business.setBusinessType(value);
            }
        }

        if (!changes.isEmpty()) {
            businessRepository.saveAndFlush(business);
            auditService.registerEvent(AuditEventType.EDICION_NEGOCIO, requester.email(), "SUCCESS",
                    "Negocio " + businessId + " editado (" + String.join(", ", changes) + ")", originIp);
        }
        return BusinessProfile.of(business);
    }

    /** En los datos opcionales una cadena vacía significa "borrar el dato". */
    private static String blankToNull(String value) {
        return value.isBlank() ? null : value.trim();
    }

    private static void apply(String label, String value, String current, Consumer<String> setter, List<String> changes) {
        if (!Objects.equals(value, current)) {
            changes.add(label);
            setter.accept(value);
        }
    }
}
