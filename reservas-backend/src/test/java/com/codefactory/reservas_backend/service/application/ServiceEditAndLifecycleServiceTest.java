package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.service.controller.dto.UpdateServiceRequest;
import com.codefactory.reservas_backend.service.domain.DuplicateServiceNameException;
import com.codefactory.reservas_backend.service.domain.ServiceNotFoundException;
import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** HU-10 (ServiceOfferingServiceImpl.update), HU-11/HU-12 (ServiceLifecycleServiceImpl) y el bloqueo de HU-22. */
@ExtendWith(MockitoExtension.class)
class ServiceEditAndLifecycleServiceTest {

    private static final UUID BUSINESS = UUID.randomUUID();
    private static final UserIdentity PROVIDER = new UserIdentity(UUID.randomUUID(), "p@example.com", "PROVEEDOR");

    @Mock
    private ServiceOfferingRepository repository;
    @Mock
    private BusinessAccessService businessAccessService;
    @Mock
    private AuditService auditService;

    private ServiceOfferingServiceImpl offering;
    private ServiceLifecycleServiceImpl lifecycle;
    private ServiceOffering existing;

    @BeforeEach
    void setUp() {
        offering = new ServiceOfferingServiceImpl(repository, businessAccessService, auditService);
        lifecycle = new ServiceLifecycleServiceImpl(repository, businessAccessService, auditService);
        existing = ServiceOffering.builder().id(UUID.randomUUID()).businessId(BUSINESS).name("Corte").description("Viejo")
                .durationMinutes(60).priceCop(50_000L).active(true).build();
    }

    private void found() {
        when(repository.findByIdForUpdate(existing.getId())).thenReturn(Optional.of(existing));
    }

    private static UpdateServiceRequest req(String name, String description, Integer minutes, Long price) {
        UpdateServiceRequest r = new UpdateServiceRequest();
        r.setName(name);
        r.setDescription(description);
        r.setDurationMinutes(minutes);
        r.setPriceCop(price);
        return r;
    }

    @Test
    void actualizaSoloLoQueLlegaYAuditaLosCambios() {
        found();

        var response = offering.update(existing.getId(), req(null, null, null, 60_000L), PROVIDER, "ip");

        assertThat(response.getPriceCop()).isEqualTo(60_000L);
        assertThat(response.getName()).isEqualTo("Corte");
        assertThat(response.getDurationMinutes()).isEqualTo(60);
        verify(repository).saveAndFlush(existing);
        verify(auditService).registerEvent(eq(AuditEventType.EDICION_SERVICIO), eq("p@example.com"), eq("SUCCESS"),
                contains("precio 50000 -> 60000"), eq("ip"));
    }

    @Test
    void unaDescripcionVaciaLaBorraYSinCambiosNoGuardaNiAudita() {
        found();

        offering.update(existing.getId(), req(null, "  ", null, null), PROVIDER, "ip");
        assertThat(existing.getDescription()).isNull();

        offering.update(existing.getId(), req("Corte", null, 60, 50_000L), PROVIDER, "ip");
        verify(repository, org.mockito.Mockito.times(1)).saveAndFlush(any());
    }

    @Test
    void unNombreRepetidoDeOtroServicioDa409() {
        found();
        when(repository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(BUSINESS, "Barba", existing.getId())).thenReturn(true);

        assertThatThrownBy(() -> offering.update(existing.getId(), req(" Barba ", null, null, null), PROVIDER, "ip"))
                .isInstanceOf(DuplicateServiceNameException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void unServicioAjenoOInexistenteNoSeEdita() {
        found();
        doThrow(new AccessDeniedException("no")).when(businessAccessService).requireOwner(BUSINESS, PROVIDER);
        UUID missing = UUID.randomUUID();
        when(repository.findByIdForUpdate(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> offering.update(existing.getId(), req(null, null, null, 1L), PROVIDER, "ip"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> offering.update(missing, req(null, null, null, 1L), PROVIDER, "ip"))
                .isInstanceOf(ServiceNotFoundException.class);
        assertThat(existing.getPriceCop()).isEqualTo(50_000L);
    }

    @Test
    void desactivarCambiaElEstadoYAudita() {
        found();

        lifecycle.deactivate(existing.getId(), PROVIDER, 2, "ip");

        assertThat(existing.isActive()).isFalse();
        verify(auditService).registerEvent(eq(AuditEventType.DESACTIVACION_SERVICIO), anyString(), eq("SUCCESS"),
                contains("2 reserva(s)"), eq("ip"));
    }

    @Test
    void reactivarUnInactivoLoActivaYUnActivoNoHaceNada() {
        found();
        existing.setActive(false);

        assertThat(lifecycle.reactivate(existing.getId(), PROVIDER, "ip").active()).isTrue();
        lifecycle.reactivate(existing.getId(), PROVIDER, "ip");

        verify(repository, org.mockito.Mockito.times(1)).saveAndFlush(existing);
        verify(auditService, org.mockito.Mockito.times(1)).registerEvent(eq(AuditEventType.REACTIVACION_SERVICIO),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void lockOwnedServiceExigePertenencia() {
        found();

        assertThat(lifecycle.lockOwnedService(existing.getId(), PROVIDER).name()).isEqualTo("Corte");
        verify(businessAccessService).requireOwner(BUSINESS, PROVIDER);
    }

    @Test
    void elBloqueoDeReservaDiceSiElServicioExisteYEstaActivo() {
        ServiceLookupServiceImpl lookup = new ServiceLookupServiceImpl(repository, null);
        UUID active = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(repository.lockSharedAndReadActive(active)).thenReturn(Optional.of(true));
        when(repository.lockSharedAndReadActive(missing)).thenReturn(Optional.empty());

        assertThat(lookup.lockForBooking(active)).isTrue();
        assertThat(lookup.lockForBooking(missing)).isFalse();
        verifyNoInteractions(auditService);
    }
}
