package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.domain.ConfirmationRequiredException;
import com.codefactory.reservas_backend.service.application.ServiceInfo;
import com.codefactory.reservas_backend.service.application.ServiceLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class ServiceDeactivationServiceImplTest {

    private static final UUID SERVICE = UUID.randomUUID();
    private static final UserIdentity PROVIDER = new UserIdentity(UUID.randomUUID(), "p@example.com", "PROVEEDOR");

    @Mock
    private ServiceLifecycleService lifecycleService;
    @Mock
    private BookingBulkCancellationService bulkCancellation;
    @InjectMocks
    private ServiceDeactivationServiceImpl service;

    private void serviceIs(boolean active) {
        when(lifecycleService.lockOwnedService(SERVICE, PROVIDER))
                .thenReturn(new ServiceInfo(SERVICE, UUID.randomUUID(), "Corte", 60, 1000L, active));
    }

    @Test
    void sinReservasFuturasDesactivaSinPedirConfirmacion() {
        serviceIs(true);
        when(bulkCancellation.countFutureOfService(SERVICE)).thenReturn(0L);

        var result = service.deactivate(SERVICE, false, PROVIDER, "ip");

        assertThat(result.active()).isFalse();
        assertThat(result.cancelledBookings()).isZero();
        verify(lifecycleService).deactivate(SERVICE, PROVIDER, 0, "ip");
        verify(bulkCancellation, never()).cancelFutureOfService(any(), any(), anyString());
    }

    @Test
    void conReservasYSinConfirmarLanza409ConLaCantidadYNoCambiaNada() {
        serviceIs(true);
        when(bulkCancellation.countFutureOfService(SERVICE)).thenReturn(3L);

        assertThatThrownBy(() -> service.deactivate(SERVICE, false, PROVIDER, "ip"))
                .isInstanceOf(ConfirmationRequiredException.class)
                .satisfies(e -> assertThat(((ConfirmationRequiredException) e).getAffectedBookings()).isEqualTo(3));
        verify(lifecycleService, never()).deactivate(any(), any(), anyInt(), anyString());
    }

    @Test
    void conConfirmacionCancelaConOrigenServicioNoDisponibleYDesactiva() {
        serviceIs(true);
        when(bulkCancellation.countFutureOfService(SERVICE)).thenReturn(3L);
        when(bulkCancellation.cancelFutureOfService(SERVICE, CancelOrigin.SERVICIO_NO_DISPONIBLE,
                ServiceDeactivationService.CANCELLATION_REASON)).thenReturn(3);

        var result = service.deactivate(SERVICE, true, PROVIDER, "ip");

        assertThat(result.cancelledBookings()).isEqualTo(3);
        verify(lifecycleService).deactivate(SERVICE, PROVIDER, 3, "ip");
    }

    @Test
    void unServicioYaInactivoNoHaceNada() {
        serviceIs(false);

        var result = service.deactivate(SERVICE, true, PROVIDER, "ip");

        assertThat(result.active()).isFalse();
        verify(bulkCancellation, never()).countFutureOfService(any());
        verify(lifecycleService, never()).deactivate(any(), any(), anyInt(), anyString());
    }
}
