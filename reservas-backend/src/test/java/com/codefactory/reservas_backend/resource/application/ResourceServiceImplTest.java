package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.resource.controller.dto.CreateResourceRequest;
import com.codefactory.reservas_backend.resource.controller.dto.ResourceResponse;
import com.codefactory.reservas_backend.resource.domain.DuplicateResourceNameException;
import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.domain.ResourceType;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
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
class ResourceServiceImplTest {

    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final UserIdentity OWNER = new UserIdentity(UUID.randomUUID(), "dueno@example.com", "PROVEEDOR");

    @Mock
    private ResourceRepository repository;
    @Mock
    private BusinessAccessService businessAccessService;
    @Mock
    private AuditService auditService;

    private ResourceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResourceServiceImpl(repository, businessAccessService, auditService);
    }

    @Test
    void createDebeGuardarUnRecursoActivoDelNegocioYAuditar() {
        when(repository.existsByBusinessIdAndNameIgnoreCase(BUSINESS_ID, "Sala 1")).thenReturn(false);
        when(repository.saveAndFlush(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(Instant.now());
            return r;
        });

        ResourceResponse response = service.create(BUSINESS_ID, request("  Sala 1  ", "sala"), OWNER, "10.0.0.1");

        ArgumentCaptor<Resource> saved = ArgumentCaptor.forClass(Resource.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Sala 1");
        assertThat(saved.getValue().getType()).isEqualTo(ResourceType.SALA);
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(saved.getValue().getBusinessId()).isEqualTo(BUSINESS_ID);
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(OWNER.id());
        assertThat(response.getType()).isEqualTo("SALA");
        verify(auditService).registerEvent(eq(AuditEventType.REGISTRO_RECURSO), eq("dueno@example.com"),
                eq("SUCCESS"), anyString(), eq("10.0.0.1"));
    }

    @Test
    void createDebeAceptarLosTresTiposSinImportarMayusculas() {
        when(repository.existsByBusinessIdAndNameIgnoreCase(any(), anyString())).thenReturn(false);
        when(repository.saveAndFlush(any(Resource.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(BUSINESS_ID, request("A", "Equipo"), OWNER, "ip").getType()).isEqualTo("EQUIPO");
        assertThat(service.create(BUSINESS_ID, request("B", " PERSONAL "), OWNER, "ip").getType()).isEqualTo("PERSONAL");
    }

    @Test
    void createDebeRechazarUnNombreRepetidoEnElMismoNegocio() {
        when(repository.existsByBusinessIdAndNameIgnoreCase(BUSINESS_ID, "Sala 1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(BUSINESS_ID, request("Sala 1", "SALA"), OWNER, "ip"))
                .isInstanceOf(DuplicateResourceNameException.class);
        verify(repository, never()).saveAndFlush(any());
        verify(auditService, never()).registerEvent(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void createNoDebeTocarElRepositorioSiElNegocioEsAjeno() {
        doThrow(new AccessDeniedException("ajeno")).when(businessAccessService).requireOwner(BUSINESS_ID, OWNER);

        assertThatThrownBy(() -> service.create(BUSINESS_ID, request("Sala 1", "SALA"), OWNER, "ip"))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void listDebeDevolverLosRecursosDelNegocioDelDuenio() {
        Resource r = Resource.builder().id(UUID.randomUUID()).businessId(BUSINESS_ID).name("Sala 1")
                .type(ResourceType.SALA).active(true).createdAt(Instant.now()).build();
        when(repository.findByBusinessIdOrderByCreatedAtAsc(BUSINESS_ID)).thenReturn(List.of(r));

        List<ResourceResponse> result = service.list(BUSINESS_ID, OWNER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Sala 1");
        verify(businessAccessService).requireOwner(BUSINESS_ID, OWNER);
    }

    @Test
    void listDebePropagarLaDenegacionSiElNegocioEsAjeno() {
        doThrow(new AccessDeniedException("ajeno")).when(businessAccessService).requireOwner(BUSINESS_ID, OWNER);

        assertThatThrownBy(() -> service.list(BUSINESS_ID, OWNER)).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).findByBusinessIdOrderByCreatedAtAsc(any());
    }

    private static CreateResourceRequest request(String name, String type) {
        CreateResourceRequest r = new CreateResourceRequest();
        r.setName(name);
        r.setType(type);
        return r;
    }
}
