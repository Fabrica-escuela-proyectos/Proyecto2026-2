package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.controller.dto.UpdateBusinessRequest;
import com.codefactory.reservas_backend.provider.domain.Business;
import com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException;
import com.codefactory.reservas_backend.provider.domain.BusinessType;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessProfileServiceImplTest {

    private static final UserIdentity PROVIDER = new UserIdentity(UUID.randomUUID(), "p@example.com", "PROVEEDOR");

    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private BusinessAccessService businessAccessService;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private BusinessProfileServiceImpl service;

    private Business business;

    @BeforeEach
    void setUp() {
        business = Business.builder().id(UUID.randomUUID()).providerId(UUID.randomUUID()).name("Viejo")
                .address("Calle 1").phone("3001234567").businessType(BusinessType.SALUD).build();
    }

    private UpdateBusinessRequest req() {
        return new UpdateBusinessRequest();
    }

    @Test
    void editaTodosLosDatosYAuditaLosCampos() {
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        UpdateBusinessRequest r = req();
        r.setName(" Barbería ");
        r.setDescription("Cortes");
        r.setAddress("Calle 10");
        r.setBusinessType("belleza");
        r.setPhone("6045551234");

        BusinessProfile profile = service.update(business.getId(), r, PROVIDER, "ip");

        assertThat(profile.name()).isEqualTo("Barbería");
        assertThat(profile.businessType()).isEqualTo("BELLEZA");
        assertThat(profile.phone()).isEqualTo("6045551234");
        verify(auditService).registerEvent(eq(AuditEventType.EDICION_NEGOCIO), eq("p@example.com"), eq("SUCCESS"),
                contains("nombre, descripción, dirección, teléfono, tipo"), eq("ip"));
    }

    @Test
    void unCampoAusenteNoCambiaYUnoVacioSeBorra() {
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        UpdateBusinessRequest r = req();
        r.setAddress("");
        r.setBusinessType(" ");

        BusinessProfile profile = service.update(business.getId(), r, PROVIDER, "ip");

        assertThat(profile.address()).isNull();
        assertThat(profile.businessType()).isNull();
        assertThat(profile.name()).isEqualTo("Viejo");
        assertThat(profile.phone()).isEqualTo("3001234567");
    }

    @Test
    void sinCambiosNoGuardaNiAudita() {
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        UpdateBusinessRequest r = req();
        r.setName("Viejo");
        r.setBusinessType("SALUD");

        service.update(business.getId(), r, PROVIDER, "ip");

        verify(businessRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void unNegocioAjenoNoSeTocaYUnoInexistenteDa404() {
        doThrow(new AccessDeniedException("no")).when(businessAccessService).requireOwner(business.getId(), PROVIDER);
        UUID missing = UUID.randomUUID();
        when(businessRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(business.getId(), req(), PROVIDER, "ip")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(missing, req(), PROVIDER, "ip")).isInstanceOf(BusinessNotFoundException.class);
        verify(businessRepository, never()).saveAndFlush(any());
    }
}
