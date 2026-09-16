package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserProvisioningService;
import com.codefactory.reservas_backend.identity.domain.DuplicateEmailException;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.infrastructure.RegistrationRateLimiter;
import com.codefactory.reservas_backend.identity.infrastructure.TooManyRequestsException;
import com.codefactory.reservas_backend.provider.controller.dto.RegisterProviderRequest;
import com.codefactory.reservas_backend.provider.controller.dto.RegisterProviderResponse;
import com.codefactory.reservas_backend.provider.domain.Business;
import com.codefactory.reservas_backend.provider.domain.Provider;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import com.codefactory.reservas_backend.provider.infrastructure.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de ProviderRegistrationService, mapeadas a los
 * escenarios Gherkin de HU - 03 Registro de proveedor de se.txt.
 */
@ExtendWith(MockitoExtension.class)
class ProviderRegistrationServiceTest {

    @Mock
    private UserProvisioningService userProvisioningService;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private RegistrationRateLimiter rateLimiter;

    private ProviderRegistrationService service;
    private RegisterProviderRequest request;

    @BeforeEach
    void setUp() {
        service = new ProviderRegistrationService(userProvisioningService, providerRepository, businessRepository,
                auditService, rateLimiter);

        request = new RegisterProviderRequest();
        request.setFullName("Carlos Gómez");
        request.setEmail("carlos@example.com");
        request.setCellphone("3019876543");
        request.setPassword("Segura#2026");
        request.setBusinessName("Centro Deportivo ABC");
    }

    @Test
    void debeRegistrarElProveedorSuNegocioYAsignarRolProveedor() {
        UUID userId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();

        when(rateLimiter.isBlocked(any())).thenReturn(false);
        when(userProvisioningService.provisionUser(
                request.getFullName(), request.getEmail(), request.getCellphone(),
                request.getPassword(), RoleName.PROVEEDOR))
                .thenReturn(new UserProvisioningService.ProvisionedUser(
                        userId, request.getFullName(), request.getEmail(), request.getCellphone(), RoleName.PROVEEDOR));
        when(providerRepository.save(any(Provider.class))).thenAnswer(inv -> {
            Provider p = inv.getArgument(0);
            p.setId(providerId);
            return p;
        });
        when(businessRepository.save(any(Business.class))).thenAnswer(inv -> {
            Business b = inv.getArgument(0);
            b.setId(businessId);
            return b;
        });

        RegisterProviderResponse response = service.register(request, "127.0.0.1");

        assertThat(response.getRole()).isEqualTo("PROVEEDOR");
        assertThat(response.getProviderId()).isEqualTo(providerId);
        assertThat(response.getBusinessName()).isEqualTo("Centro Deportivo ABC");
        verify(providerRepository).save(org.mockito.ArgumentMatchers.argThat(p -> p.getUserId().equals(userId)));
        verify(auditService).registerEvent(eq(AuditEventType.REGISTRO_PROVEEDOR), eq(request.getEmail()), eq("SUCCESS"), any(), any());
    }

    @Test
    void debeBloquearTemporalmenteTrasMultiplesIntentos() {
        when(rateLimiter.isBlocked(any())).thenReturn(true);

        assertThatThrownBy(() -> service.register(request, "127.0.0.1"))
                .isInstanceOf(TooManyRequestsException.class);
        verify(providerRepository, never()).save(any());
    }

    @Test
    void debePropagarYAuditarCorreoDuplicadoSinCrearProviderNiBusiness() {
        when(rateLimiter.isBlocked(any())).thenReturn(false);
        when(userProvisioningService.provisionUser(any(), any(), any(), any(), eq(RoleName.PROVEEDOR)))
                .thenThrow(new DuplicateEmailException("El correo electrónico ya está en uso"));

        assertThatThrownBy(() -> service.register(request, "127.0.0.1"))
                .isInstanceOf(DuplicateEmailException.class);

        verify(providerRepository, never()).save(any());
        verify(businessRepository, never()).save(any());
        verify(auditService).registerEvent(eq(AuditEventType.REGISTRO_PROVEEDOR), eq(request.getEmail()), eq("REJECTED"), any(), any());
    }
}
