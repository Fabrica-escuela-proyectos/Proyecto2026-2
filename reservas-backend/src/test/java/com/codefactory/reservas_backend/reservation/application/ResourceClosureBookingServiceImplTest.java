package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.domain.ConfirmationRequiredException;
import com.codefactory.reservas_backend.resource.application.ClosureInfo;
import com.codefactory.reservas_backend.resource.application.ClosurePeriod;
import com.codefactory.reservas_backend.resource.application.ResourceClosureService;
import com.codefactory.reservas_backend.resource.application.ResourceLifecycleService;
import com.codefactory.reservas_backend.resource.domain.ClosureConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceClosureBookingServiceImplTest {

    private static final UUID RESOURCE = UUID.randomUUID();
    private static final UserIdentity PROVIDER = new UserIdentity(UUID.randomUUID(), "p@example.com", "PROVEEDOR");
    private static final ClosurePeriod PERIOD = new ClosurePeriod(Instant.parse("2026-11-20T14:00:00Z"), Instant.parse("2026-11-20T17:00:00Z"));

    @Mock
    private ResourceLifecycleService lifecycleService;
    @Mock
    private ResourceClosureService closureService;
    @Mock
    private BookingBulkCancellationService bulkCancellation;
    @InjectMocks
    private ResourceClosureBookingServiceImpl service;

    @BeforeEach
    void setUp() {
        when(closureService.resolvePeriod("2026-11-20", null, null)).thenReturn(PERIOD);
    }

    private ClosureInfo info() {
        return new ClosureInfo(UUID.randomUUID(), RESOURCE, "2026-11-20T00:00", "2026-11-21T00:00", true, null, Instant.now());
    }

    @Test
    void sinReservasAfectadasCierraDirectoBloqueandoAntesDeContar() {
        when(bulkCancellation.countFutureOfResourceIn(RESOURCE, PERIOD.start(), PERIOD.end())).thenReturn(0L);
        when(closureService.create(RESOURCE, PERIOD, "Festivo", 0, PROVIDER, "ip")).thenReturn(info());

        var result = service.close(RESOURCE, "2026-11-20", null, null, "Festivo", false, PROVIDER, "ip");

        assertThat(result.cancelledBookings()).isZero();
        assertThat(result.message()).isEqualTo("El recurso quedó cerrado en ese periodo");
        InOrder order = inOrder(lifecycleService, closureService, bulkCancellation);
        order.verify(lifecycleService).lockOwnedResource(RESOURCE, PROVIDER);
        order.verify(closureService).requireNoOverlap(RESOURCE, PERIOD);
        order.verify(bulkCancellation).countFutureOfResourceIn(RESOURCE, PERIOD.start(), PERIOD.end());
        verify(bulkCancellation, never()).cancelFutureOfResourceIn(any(), any(), any(), any(), anyString());
    }

    @Test
    void conReservasYSinConfirmar409SinCrearNada() {
        when(bulkCancellation.countFutureOfResourceIn(RESOURCE, PERIOD.start(), PERIOD.end())).thenReturn(2L);

        assertThatThrownBy(() -> service.close(RESOURCE, "2026-11-20", null, null, null, false, PROVIDER, "ip"))
                .isInstanceOf(ConfirmationRequiredException.class);
        verify(closureService, never()).create(any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void conConfirmacionCancelaLasAfectadasYCierra() {
        when(bulkCancellation.countFutureOfResourceIn(RESOURCE, PERIOD.start(), PERIOD.end())).thenReturn(2L);
        when(bulkCancellation.cancelFutureOfResourceIn(RESOURCE, PERIOD.start(), PERIOD.end(), CancelOrigin.RECURSO_NO_DISPONIBLE,
                ResourceClosureBookingService.CANCELLATION_REASON)).thenReturn(2);
        when(closureService.create(RESOURCE, PERIOD, null, 2, PROVIDER, "ip")).thenReturn(info());

        var result = service.close(RESOURCE, "2026-11-20", null, null, null, true, PROVIDER, "ip");

        assertThat(result.cancelledBookings()).isEqualTo(2);
        assertThat(result.message()).contains("se cancelaron 2");
    }

    @Test
    void unTraslapeConOtroCierreSeRechazaAntesDeContar() {
        doThrow(new ClosureConflictException("se cruza")).when(closureService).requireNoOverlap(RESOURCE, PERIOD);

        assertThatThrownBy(() -> service.close(RESOURCE, "2026-11-20", null, null, null, true, PROVIDER, "ip"))
                .isInstanceOf(ClosureConflictException.class);
        verify(bulkCancellation, never()).countFutureOfResourceIn(any(), any(), any());
    }
}
