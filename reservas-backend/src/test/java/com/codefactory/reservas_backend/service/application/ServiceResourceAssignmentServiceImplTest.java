package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.resource.application.ResourceInfo;
import com.codefactory.reservas_backend.resource.application.ResourceLookupService;
import com.codefactory.reservas_backend.service.controller.dto.ServiceResourcesDtos.ServiceResourcesResponse;
import com.codefactory.reservas_backend.service.domain.InvalidResourceAssignmentException;
import com.codefactory.reservas_backend.service.domain.ServiceNotFoundException;
import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.domain.ServiceResource;
import com.codefactory.reservas_backend.service.domain.ServiceResourceId;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import com.codefactory.reservas_backend.service.infrastructure.ServiceResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceResourceAssignmentServiceImplTest {

    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final UUID R1 = UUID.randomUUID();
    private static final UUID R2 = UUID.randomUUID();
    private static final UUID R3 = UUID.randomUUID();
    private static final UserIdentity OWNER = new UserIdentity(UUID.randomUUID(), "dueno@example.com", "PROVEEDOR");

    @Mock
    private ServiceOfferingRepository serviceRepository;
    @Mock
    private ServiceResourceRepository assignmentRepository;
    @Mock
    private BusinessAccessService businessAccessService;
    @Mock
    private ResourceLookupService resourceLookupService;
    @Mock
    private AuditService auditService;

    private ServiceResourceAssignmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ServiceResourceAssignmentServiceImpl(serviceRepository, assignmentRepository,
                businessAccessService, resourceLookupService, auditService);
    }

    private void stubService() {
        when(serviceRepository.findByIdForUpdate(SERVICE_ID)).thenReturn(Optional.of(
                ServiceOffering.builder().id(SERVICE_ID).businessId(BUSINESS_ID).name("Corte").build()));
    }

    private static ResourceInfo info(UUID id, String name) {
        return new ResourceInfo(id, BUSINESS_ID, name, "SALA", true);
    }

    @Test
    void replaceDebeAsignarLosRecursosNuevosYAuditar() {
        stubService();
        when(resourceLookupService.findInBusiness(eq(BUSINESS_ID), any(Collection.class)))
                .thenReturn(List.of(info(R2, "Sala B"), info(R1, "Sala A")));
        when(assignmentRepository.findByIdServiceId(SERVICE_ID)).thenReturn(List.of());

        ServiceResourcesResponse response = service.replaceResources(SERVICE_ID, List.of(R1, R2), OWNER, "10.0.0.1");

        ArgumentCaptor<List<ServiceResource>> added = ArgumentCaptor.forClass(List.class);
        verify(assignmentRepository).saveAll(added.capture());
        assertThat(added.getValue()).extracting(a -> a.getId().getResourceId()).containsExactlyInAnyOrder(R1, R2);
        assertThat(response.resources()).extracting("name").containsExactly("Sala A", "Sala B");
        verify(auditService).registerEvent(eq(AuditEventType.ASIGNACION_RECURSOS), eq("dueno@example.com"),
                eq("SUCCESS"), anyString(), eq("10.0.0.1"));
    }

    @Test
    void replaceDebeAplicarSoloLaDiferenciaConElConjuntoActual() {
        stubService();
        when(resourceLookupService.findInBusiness(eq(BUSINESS_ID), any(Collection.class)))
                .thenReturn(List.of(info(R2, "B"), info(R3, "C")));
        when(assignmentRepository.findByIdServiceId(SERVICE_ID))
                .thenReturn(List.of(new ServiceResource(SERVICE_ID, R1), new ServiceResource(SERVICE_ID, R2)));

        service.replaceResources(SERVICE_ID, List.of(R2, R3), OWNER, "ip");

        ArgumentCaptor<List<ServiceResourceId>> removed = ArgumentCaptor.forClass(List.class);
        verify(assignmentRepository).deleteAllById(removed.capture());
        assertThat(removed.getValue()).extracting(ServiceResourceId::getResourceId).containsExactly(R1);
        ArgumentCaptor<List<ServiceResource>> added = ArgumentCaptor.forClass(List.class);
        verify(assignmentRepository).saveAll(added.capture());
        assertThat(added.getValue()).extracting(a -> a.getId().getResourceId()).containsExactly(R3);
    }

    @Test
    void replaceConElMismoConjuntoNoCambiaNadaNiAudita() {
        stubService();
        when(resourceLookupService.findInBusiness(eq(BUSINESS_ID), any(Collection.class))).thenReturn(List.of(info(R1, "A")));
        when(assignmentRepository.findByIdServiceId(SERVICE_ID)).thenReturn(List.of(new ServiceResource(SERVICE_ID, R1)));

        service.replaceResources(SERVICE_ID, List.of(R1), OWNER, "ip");

        verify(assignmentRepository).deleteAllById(List.of());
        verify(assignmentRepository).saveAll(List.of());
        verify(auditService, never()).registerEvent(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void replaceDebeIgnorarIdsRepetidos() {
        stubService();
        when(resourceLookupService.findInBusiness(eq(BUSINESS_ID), any(Collection.class))).thenReturn(List.of(info(R1, "A")));
        when(assignmentRepository.findByIdServiceId(SERVICE_ID)).thenReturn(List.of());

        ServiceResourcesResponse response = service.replaceResources(SERVICE_ID, List.of(R1, R1, R1), OWNER, "ip");

        assertThat(response.resources()).hasSize(1);
    }

    @Test
    void replaceConListaVaciaQuitaTodasLasAsignaciones() {
        stubService();
        when(resourceLookupService.findInBusiness(eq(BUSINESS_ID), any(Collection.class))).thenReturn(List.of());
        when(assignmentRepository.findByIdServiceId(SERVICE_ID)).thenReturn(List.of(new ServiceResource(SERVICE_ID, R1)));

        ServiceResourcesResponse response = service.replaceResources(SERVICE_ID, List.of(), OWNER, "ip");

        assertThat(response.resources()).isEmpty();
        ArgumentCaptor<List<ServiceResourceId>> removed = ArgumentCaptor.forClass(List.class);
        verify(assignmentRepository).deleteAllById(removed.capture());
        assertThat(removed.getValue()).hasSize(1);
    }

    @Test
    void replaceDebeRechazarTodoSiUnRecursoNoExisteOEsDeOtroNegocio() {
        stubService();
        // Se piden dos y solo uno es válido para el negocio.
        when(resourceLookupService.findInBusiness(eq(BUSINESS_ID), any(Collection.class))).thenReturn(List.of(info(R1, "A")));

        assertThatThrownBy(() -> service.replaceResources(SERVICE_ID, List.of(R1, R2), OWNER, "ip"))
                .isInstanceOf(InvalidResourceAssignmentException.class);
        verify(assignmentRepository, never()).saveAll(anyIterable());
        verify(assignmentRepository, never()).deleteAllById(anyIterable());
        verify(auditService, never()).registerEvent(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void replaceDebeFallarSiElServicioNoExiste() {
        when(serviceRepository.findByIdForUpdate(SERVICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceResources(SERVICE_ID, List.of(R1), OWNER, "ip"))
                .isInstanceOf(ServiceNotFoundException.class);
    }

    @Test
    void replaceDebeDenegarSiElServicioEsDeOtroProveedor() {
        stubService();
        doThrow(new AccessDeniedException("ajeno")).when(businessAccessService).requireOwner(BUSINESS_ID, OWNER);

        assertThatThrownBy(() -> service.replaceResources(SERVICE_ID, List.of(R1), OWNER, "ip"))
                .isInstanceOf(AccessDeniedException.class);
        verify(resourceLookupService, never()).findInBusiness(any(), any());
        verify(assignmentRepository, never()).saveAll(anyIterable());
    }

    @Test
    void getResourcesDebeDevolverLosRecursosAsignadosOrdenadosPorNombre() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(
                ServiceOffering.builder().id(SERVICE_ID).businessId(BUSINESS_ID).name("Corte").build()));
        when(assignmentRepository.findByIdServiceId(SERVICE_ID))
                .thenReturn(List.of(new ServiceResource(SERVICE_ID, R2), new ServiceResource(SERVICE_ID, R1)));
        when(resourceLookupService.findByIds(any(Collection.class))).thenReturn(List.of(info(R2, "zeta"), info(R1, "Alfa")));

        ServiceResourcesResponse response = service.getResources(SERVICE_ID, OWNER);

        assertThat(response.resources()).extracting("name").containsExactly("Alfa", "zeta");
        verify(businessAccessService).requireOwner(BUSINESS_ID, OWNER);
    }

    @Test
    void getResourcesDebeFallarSiElServicioNoExiste() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getResources(SERVICE_ID, OWNER)).isInstanceOf(ServiceNotFoundException.class);
    }
}
