package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.domain.ResourceNotFoundException;
import com.codefactory.reservas_backend.resource.domain.ResourceType;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceLifecycleServiceImplTest {

    private static final UUID RESOURCE_ID = UUID.randomUUID();
    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final UserIdentity OWNER = new UserIdentity(UUID.randomUUID(), "dueno@example.com", "PROVEEDOR");

    @Mock
    private ResourceRepository repository;
    @Mock
    private BusinessAccessService businessAccessService;
    @Mock
    private AuditService auditService;

    private ResourceLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResourceLifecycleServiceImpl(repository, businessAccessService, auditService);
    }

    private Resource resource(boolean active) {
        return Resource.builder().id(RESOURCE_ID).businessId(BUSINESS_ID).name("Sala 1").type(ResourceType.SALA).active(active).build();
    }

    @Test
    void lockOwnedResourceDebeVerificarQueElSolicitanteEsElDuenio() {
        when(repository.findByIdForUpdate(RESOURCE_ID)).thenReturn(Optional.of(resource(true)));

        ResourceInfo info = service.lockOwnedResource(RESOURCE_ID, OWNER);

        assertThat(info).isEqualTo(new ResourceInfo(RESOURCE_ID, BUSINESS_ID, "Sala 1", "SALA", true));
        verify(businessAccessService).requireOwner(BUSINESS_ID, OWNER);
    }

    @Test
    void lockOwnedResourceDebeFallarSiNoExisteOEsDeOtroProveedor() {
        when(repository.findByIdForUpdate(RESOURCE_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.lockOwnedResource(RESOURCE_ID, OWNER)).isInstanceOf(ResourceNotFoundException.class);

        when(repository.findByIdForUpdate(RESOURCE_ID)).thenReturn(Optional.of(resource(true)));
        doThrow(new AccessDeniedException("ajeno")).when(businessAccessService).requireOwner(BUSINESS_ID, OWNER);
        assertThatThrownBy(() -> service.lockOwnedResource(RESOURCE_ID, OWNER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deactivateDebeMarcarInactivoYAuditar() {
        Resource r = resource(true);
        when(repository.findByIdForUpdate(RESOURCE_ID)).thenReturn(Optional.of(r));

        service.deactivate(RESOURCE_ID, OWNER, 3, "10.0.0.1");

        assertThat(r.isActive()).isFalse();
        verify(repository).saveAndFlush(r);
        verify(auditService).registerEvent(eq(AuditEventType.DESACTIVACION_RECURSO), eq("dueno@example.com"),
                eq("SUCCESS"), contains("3 reserva"), eq("10.0.0.1"));
    }

    @Test
    void reactivateDebeActivarElMismoRecursoYAuditar() {
        Resource r = resource(false);
        when(repository.findByIdForUpdate(RESOURCE_ID)).thenReturn(Optional.of(r));

        ResourceInfo info = service.reactivate(RESOURCE_ID, OWNER, "ip");

        assertThat(r.isActive()).isTrue();
        assertThat(info.active()).isTrue();
        assertThat(info.id()).isEqualTo(RESOURCE_ID);
        verify(auditService).registerEvent(eq(AuditEventType.REACTIVACION_RECURSO), eq("dueno@example.com"),
                eq("SUCCESS"), anyString(), eq("ip"));
    }

    @Test
    void reactivateUnRecursoYaActivoEsIdempotenteYNoAudita() {
        when(repository.findByIdForUpdate(RESOURCE_ID)).thenReturn(Optional.of(resource(true)));

        assertThat(service.reactivate(RESOURCE_ID, OWNER, "ip").active()).isTrue();

        verify(repository, never()).saveAndFlush(any());
        verify(auditService, never()).registerEvent(any(), anyString(), anyString(), anyString(), anyString());
    }
}
