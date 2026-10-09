package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.controller.dto.BookingLeadTimeResponse;
import com.codefactory.reservas_backend.provider.domain.Business;
import com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessSettingsServiceImplTest {

    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final UserIdentity OWNER = new UserIdentity(UUID.randomUUID(), "dueno@example.com", "PROVEEDOR");

    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private BusinessAccessService businessAccessService;
    @Mock
    private AuditService auditService;

    private BusinessSettingsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BusinessSettingsServiceImpl(businessRepository, businessAccessService, auditService);
    }

    private Business business(int hours) {
        return Business.builder().id(BUSINESS_ID).providerId(UUID.randomUUID()).name("Negocio")
                .minAdvanceHours(hours).build();
    }

    @Test
    void unNegocioNuevoTieneUnaHoraDeAntelacionPorDefecto() {
        Business b = Business.builder().name("Nuevo").providerId(UUID.randomUUID()).build();

        assertThat(b.getMinAdvanceHours()).isEqualTo(1);
    }

    @Test
    void getDebeDevolverLaAntelacionVigenteDelDuenio() {
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business(4)));

        BookingLeadTimeResponse response = service.getBookingLeadTime(BUSINESS_ID, OWNER);

        assertThat(response.hours()).isEqualTo(4);
        assertThat(response.businessId()).isEqualTo(BUSINESS_ID);
        verify(businessAccessService).requireOwner(BUSINESS_ID, OWNER);
    }

    @Test
    void updateDebeGuardarElNuevoValorYAuditar() {
        Business b = business(1);
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(b));

        BookingLeadTimeResponse response = service.updateBookingLeadTime(BUSINESS_ID, 2, OWNER, "10.0.0.1");

        assertThat(response.hours()).isEqualTo(2);
        assertThat(b.getMinAdvanceHours()).isEqualTo(2);
        verify(businessRepository).save(b);
        verify(auditService).registerEvent(eq(AuditEventType.CONFIGURACION_NEGOCIO), eq("dueno@example.com"),
                eq("SUCCESS"), anyString(), eq("10.0.0.1"));
    }

    @Test
    void updateNoDebeModificarNadaSiElNegocioEsAjeno() {
        doThrow(new AccessDeniedException("ajeno")).when(businessAccessService).requireOwner(BUSINESS_ID, OWNER);

        assertThatThrownBy(() -> service.updateBookingLeadTime(BUSINESS_ID, 2, OWNER, "ip"))
                .isInstanceOf(AccessDeniedException.class);
        verify(businessRepository, never()).save(any());
        verify(auditService, never()).registerEvent(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void getDebeFallarSiElNegocioNoExiste() {
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBookingLeadTime(BUSINESS_ID, OWNER))
                .isInstanceOf(BusinessNotFoundException.class);
    }

    @Test
    void minAdvanceHoursOfDebeServirAOtrosModulosSinComprobarAlUsuario() {
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business(24)));

        assertThat(service.minAdvanceHoursOf(BUSINESS_ID)).isEqualTo(24);
        verify(businessAccessService, never()).requireOwner(any(), any());
    }

    @Test
    void minAdvanceHoursOfDebeFallarSiElNegocioNoExiste() {
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.minAdvanceHoursOf(BUSINESS_ID))
                .isInstanceOf(BusinessNotFoundException.class);
    }
}
