package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.service.controller.dto.CreateServiceRequest;
import com.codefactory.reservas_backend.service.controller.dto.ServiceResponse;
import com.codefactory.reservas_backend.service.domain.DuplicateServiceNameException;
import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
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
class ServiceOfferingServiceImplTest {

    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final UserIdentity OWNER = new UserIdentity(UUID.randomUUID(), "dueno@example.com", "PROVEEDOR");

    @Mock
    private ServiceOfferingRepository repository;
    @Mock
    private BusinessAccessService businessAccessService;
    @Mock
    private AuditService auditService;

    private ServiceOfferingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ServiceOfferingServiceImpl(repository, businessAccessService, auditService);
    }

    @Test
    void createDebeGuardarUnServicioActivoConLosDatosNormalizadosYAuditar() {
        when(repository.existsByBusinessIdAndNameIgnoreCase(BUSINESS_ID, "Corte de cabello")).thenReturn(false);
        when(repository.saveAndFlush(any(ServiceOffering.class))).thenAnswer(inv -> {
            ServiceOffering s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            s.setCreatedAt(Instant.now());
            return s;
        });

        ServiceResponse response = service.create(BUSINESS_ID,
                request("  Corte de cabello  ", "  Con lavado  ", 45, 30_000L), OWNER, "10.0.0.1");

        ArgumentCaptor<ServiceOffering> saved = ArgumentCaptor.forClass(ServiceOffering.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Corte de cabello");
        assertThat(saved.getValue().getDescription()).isEqualTo("Con lavado");
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(saved.getValue().getBusinessId()).isEqualTo(BUSINESS_ID);
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(OWNER.id());
        assertThat(response.isActive()).isTrue();
        assertThat(response.getDurationMinutes()).isEqualTo(45);
        assertThat(response.getPriceCop()).isEqualTo(30_000L);
        verify(auditService).registerEvent(eq(AuditEventType.CREACION_SERVICIO), eq("dueno@example.com"),
                eq("SUCCESS"), anyString(), eq("10.0.0.1"));
    }

    @Test
    void createDebePermitirPrecioCeroParaServiciosGratuitos() {
        when(repository.existsByBusinessIdAndNameIgnoreCase(any(), anyString())).thenReturn(false);
        when(repository.saveAndFlush(any(ServiceOffering.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceResponse response = service.create(BUSINESS_ID, request("Asesoría", null, 30, 0L), OWNER, "ip");

        assertThat(response.getPriceCop()).isZero();
        assertThat(response.getDescription()).isNull();
    }

    @Test
    void createDebeTratarUnaDescripcionEnBlancoComoAusente() {
        when(repository.existsByBusinessIdAndNameIgnoreCase(any(), anyString())).thenReturn(false);
        when(repository.saveAndFlush(any(ServiceOffering.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceResponse response = service.create(BUSINESS_ID, request("Masaje", "   ", 60, 80_000L), OWNER, "ip");

        assertThat(response.getDescription()).isNull();
    }

    @Test
    void createDebeRechazarUnNombreRepetidoEnElMismoNegocio() {
        when(repository.existsByBusinessIdAndNameIgnoreCase(BUSINESS_ID, "Corte")).thenReturn(true);

        assertThatThrownBy(() -> service.create(BUSINESS_ID, request("Corte", null, 30, 10_000L), OWNER, "ip"))
                .isInstanceOf(DuplicateServiceNameException.class);
        verify(repository, never()).saveAndFlush(any());
        verify(auditService, never()).registerEvent(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void createNoDebeTocarElRepositorioSiElNegocioEsAjeno() {
        doThrow(new AccessDeniedException("ajeno")).when(businessAccessService).requireOwner(BUSINESS_ID, OWNER);

        assertThatThrownBy(() -> service.create(BUSINESS_ID, request("Corte", null, 30, 10_000L), OWNER, "ip"))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void listDebeDevolverLosServiciosDelNegocioDelDuenio() {
        ServiceOffering s = ServiceOffering.builder().id(UUID.randomUUID()).businessId(BUSINESS_ID).name("Corte")
                .durationMinutes(30).priceCop(10_000L).active(true).createdAt(Instant.now()).build();
        when(repository.findByBusinessIdOrderByCreatedAtAsc(BUSINESS_ID)).thenReturn(List.of(s));

        List<ServiceResponse> result = service.list(BUSINESS_ID, OWNER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Corte");
        verify(businessAccessService).requireOwner(BUSINESS_ID, OWNER);
    }

    @Test
    void listDebeDevolverVacioSiElNegocioNoTieneServicios() {
        when(repository.findByBusinessIdOrderByCreatedAtAsc(BUSINESS_ID)).thenReturn(List.of());

        assertThat(service.list(BUSINESS_ID, OWNER)).isEmpty();
    }

    @Test
    void listDebePropagarLaDenegacionSiElNegocioEsAjeno() {
        doThrow(new AccessDeniedException("ajeno")).when(businessAccessService).requireOwner(BUSINESS_ID, OWNER);

        assertThatThrownBy(() -> service.list(BUSINESS_ID, OWNER)).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).findByBusinessIdOrderByCreatedAtAsc(any());
    }

    private static CreateServiceRequest request(String name, String description, Integer duration, Long price) {
        CreateServiceRequest r = new CreateServiceRequest();
        r.setName(name);
        r.setDescription(description);
        r.setDurationMinutes(duration);
        r.setPriceCop(price);
        return r;
    }
}
