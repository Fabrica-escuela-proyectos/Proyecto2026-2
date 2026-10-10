package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.ResourceDeactivationService.DeactivationResult;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.domain.ConfirmationRequiredException;
import com.codefactory.reservas_backend.resource.application.ResourceInfo;
import com.codefactory.reservas_backend.resource.application.ResourceLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceDeactivationServiceImplTest {

    private static final UUID RESOURCE_ID = UUID.randomUUID();
    private static final UserIdentity OWNER = new UserIdentity(UUID.randomUUID(), "dueno@example.com", "PROVEEDOR");

    @Mock
    private ResourceLifecycleService lifecycleService;
    @Mock
    private BookingBulkCancellationService bulkCancellation;

    private ResourceDeactivationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResourceDeactivationServiceImpl(lifecycleService, bulkCancellation);
    }

    private void stubResource(boolean active) {
        when(lifecycleService.lockOwnedResource(RESOURCE_ID, OWNER))
                .thenReturn(new ResourceInfo(RESOURCE_ID, UUID.randomUUID(), "Sala 1", "SALA", active));
    }

    @Test
    void sinReservasFuturasDesactivaDirectoSinPedirConfirmacion() {
        stubResource(true);
        when(bulkCancellation.countFutureOfResource(RESOURCE_ID)).thenReturn(0L);

        DeactivationResult result = service.deactivate(RESOURCE_ID, false, OWNER, "ip");

        assertThat(result.active()).isFalse();
        assertThat(result.cancelledBookings()).isZero();
        verify(lifecycleService).deactivate(RESOURCE_ID, OWNER, 0, "ip");
        verify(bulkCancellation, never()).cancelFutureOfResource(any(), any(), anyString());
    }

    @Test
    void conReservasFuturasYSinConfirmarInformaLaCantidadYNoCambiaNada() {
        stubResource(true);
        when(bulkCancellation.countFutureOfResource(RESOURCE_ID)).thenReturn(3L);

        assertThatThrownBy(() -> service.deactivate(RESOURCE_ID, false, OWNER, "ip"))
                .isInstanceOfSatisfying(ConfirmationRequiredException.class, e -> {
                    assertThat(e.getAffectedBookings()).isEqualTo(3);
                    assertThat(e.getMessage()).contains("3 reserva");
                });
        verify(bulkCancellation, never()).cancelFutureOfResource(any(), any(), anyString());
        verify(lifecycleService, never()).deactivate(any(), any(), anyInt(), anyString());
    }

    @Test
    void conReservasFuturasYConfirmacionLasCancelaConOrigenRecursoNoDisponibleYDesactiva() {
        stubResource(true);
        when(bulkCancellation.countFutureOfResource(RESOURCE_ID)).thenReturn(2L);
        when(bulkCancellation.cancelFutureOfResource(RESOURCE_ID, CancelOrigin.RECURSO_NO_DISPONIBLE,
                ResourceDeactivationService.CANCELLATION_REASON)).thenReturn(2);

        DeactivationResult result = service.deactivate(RESOURCE_ID, true, OWNER, "ip");

        assertThat(result.cancelledBookings()).isEqualTo(2);
        assertThat(result.active()).isFalse();
        verify(lifecycleService).deactivate(RESOURCE_ID, OWNER, 2, "ip");
    }

    @Test
    void unRecursoYaInactivoEsIdempotenteYNoTocaReservas() {
        stubResource(false);

        DeactivationResult result = service.deactivate(RESOURCE_ID, true, OWNER, "ip");

        assertThat(result.active()).isFalse();
        assertThat(result.cancelledBookings()).isZero();
        verify(bulkCancellation, never()).countFutureOfResource(any());
        verify(lifecycleService, never()).deactivate(any(), any(), anyInt(), anyString());
    }

    @Test
    void unRecursoAjenoSeDenegaAntesDeContarONadaMas() {
        when(lifecycleService.lockOwnedResource(RESOURCE_ID, OWNER)).thenThrow(new AccessDeniedException("ajeno"));

        assertThatThrownBy(() -> service.deactivate(RESOURCE_ID, true, OWNER, "ip")).isInstanceOf(AccessDeniedException.class);
        verify(bulkCancellation, never()).countFutureOfResource(any());
    }
}
