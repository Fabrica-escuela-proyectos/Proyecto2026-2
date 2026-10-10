package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingBulkCancellationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-14T13:00:00Z");
    private static final UUID RESOURCE_ID = UUID.randomUUID();

    @Mock
    private BookingRepository repository;

    private BookingBulkCancellationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookingBulkCancellationServiceImpl(repository, Clock.fixed(NOW, ZoneId.of("America/Bogota")));
    }

    @Test
    void cuentaLasReservasFuturasDesdeLaHoraActualDelNegocio() {
        when(repository.countFutureConfirmedOfResource(RESOURCE_ID, NOW)).thenReturn(4L);

        assertThat(service.countFutureOfResource(RESOURCE_ID)).isEqualTo(4);
    }

    @Test
    void cancelaLasFuturasDeUnClienteYDeVariosNegocios() {
        UUID client = UUID.randomUUID();
        java.util.List<UUID> businesses = java.util.List.of(UUID.randomUUID(), UUID.randomUUID());
        when(repository.cancelFutureConfirmedOfClient(client, CancelOrigin.ELIMINACION_CUENTA, "m", NOW)).thenReturn(1);
        when(repository.cancelFutureConfirmedOfBusinesses(businesses, CancelOrigin.ELIMINACION_CUENTA, "m", NOW)).thenReturn(7);

        assertThat(service.cancelFutureOfClient(client, CancelOrigin.ELIMINACION_CUENTA, "m")).isEqualTo(1);
        assertThat(service.cancelFutureOfBusinesses(businesses, CancelOrigin.ELIMINACION_CUENTA, "m")).isEqualTo(7);
    }

    @Test
    void sinNegociosNoConsultaLaBase() {
        assertThat(service.cancelFutureOfBusinesses(java.util.List.of(), CancelOrigin.ELIMINACION_CUENTA, "m")).isZero();
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void cancelaLasFuturasConElOrigenYElMotivoIndicadosYDevuelveCuantas() {
        when(repository.cancelFutureConfirmedOfResource(RESOURCE_ID, CancelOrigin.RECURSO_NO_DISPONIBLE, "motivo", NOW)).thenReturn(2);

        assertThat(service.cancelFutureOfResource(RESOURCE_ID, CancelOrigin.RECURSO_NO_DISPONIBLE, "motivo")).isEqualTo(2);
    }
}
