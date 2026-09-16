package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserProvisioningService;
import com.codefactory.reservas_backend.identity.domain.DuplicateEmailException;
import com.codefactory.reservas_backend.identity.domain.DuplicatePhoneException;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.infrastructure.RegistrationRateLimiter;
import com.codefactory.reservas_backend.identity.infrastructure.TooManyRequestsException;
import com.codefactory.reservas_backend.provider.controller.dto.RegisterProviderRequest;
import com.codefactory.reservas_backend.provider.controller.dto.RegisterProviderResponse;
import com.codefactory.reservas_backend.provider.domain.Business;
import com.codefactory.reservas_backend.provider.domain.Provider;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import com.codefactory.reservas_backend.provider.infrastructure.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa HU-03 - Registro de proveedor de servicios. Coordina el caso de
 * uso: crea la cuenta de acceso delegando en identity (UserProvisioningService,
 * el contrato que Identity & Access expone para esto — ADR-003), crea el
 * Provider y su Business, y audita el evento, sin acceder nunca directamente
 * a UserRepository/RoleRepository de identity.
 */
@Service
@RequiredArgsConstructor
public class ProviderRegistrationService {

    private final UserProvisioningService userProvisioningService;
    private final ProviderRepository providerRepository;
    private final BusinessRepository businessRepository;
    private final AuditService auditService;
    private final RegistrationRateLimiter rateLimiter;

    @Transactional
    public RegisterProviderResponse register(RegisterProviderRequest request, String originIp) {

        // Mismo mecanismo y mismo contador que HU-01 (ver Javadoc de
        // RegistrationRateLimiter) — errores-api-sprint-1.md sección 12
        // también lista 429 como error relevante de HU-03.
        if (rateLimiter.isBlocked(originIp)) {
            throw new TooManyRequestsException(
                    "Se han detectado demasiadas solicitudes de registro desde este origen. Intenta más tarde.");
        }
        rateLimiter.registerAttempt(originIp);

        UserProvisioningService.ProvisionedUser provisionedUser;
        try {
            provisionedUser = userProvisioningService.provisionUser(
                    request.getFullName(), request.getEmail(), request.getCellphone(),
                    request.getPassword(), RoleName.PROVEEDOR);
        } catch (DuplicateEmailException | DuplicatePhoneException rejected) {
            auditService.registerEvent(AuditEventType.REGISTRO_PROVEEDOR, request.getEmail(), "REJECTED",
                    rejected.getMessage(), originIp);
            throw rejected;
        }

        Provider provider = providerRepository.save(Provider.builder()
                .userId(provisionedUser.id())
                .build());

        Business business = businessRepository.save(Business.builder()
                .providerId(provider.getId())
                .name(request.getBusinessName())
                .build());

        // Escenario "Registro exitoso de proveedor con datos válidos".
        auditService.registerEvent(AuditEventType.REGISTRO_PROVEEDOR, provisionedUser.email(), "SUCCESS", null, originIp);

        return new RegisterProviderResponse(
                provisionedUser.id(), provisionedUser.fullName(), provisionedUser.email(),
                RoleName.PROVEEDOR.name(), provider.getId(), business.getId(), business.getName());
    }
}
