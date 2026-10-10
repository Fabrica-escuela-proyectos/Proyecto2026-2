package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingItem;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingNotCancellableException;
import com.codefactory.reservas_backend.reservation.domain.BookingNotFoundException;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCancellationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-14T13:00:00Z");
    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final UserIdentity CLIENT = new UserIdentity(UUID.randomUUID(), "cliente@example.com", "CLIENTE");

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private AuditService auditService;

    private BookingCancellationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookingCancellationServiceImpl(bookingRepository, auditService,
                Clock.fixed(NOW, ZoneId.of("America/Bogota")));
    }

    private Booking booking(UUID clientId, BookingStatus status, Instant startAt) {
        return Booking.builder().id(BOOKING_ID).clientId(clientId).clientName("Ana").clientEmail("cliente@example.com")
                .businessId(UUID.randomUUID()).businessName("Barbería").serviceId(UUID.randomUUID()).serviceName("Corte")
                .resourceId(UUID.randomUUID()).resourceName("Sala 1").startAt(startAt).endAt(startAt.plusSeconds(3600))
                .status(status).priceCop(25_000L).createdAt(NOW).build();
    }

    private void stub(Booking b) {
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(b));
    }

    @Test
    void cancelDebePasarLaReservaACanceladaConOrigenClienteMotivoYFecha() {
        Booking b = booking(CLIENT.id(), BookingStatus.CONFIRMADA, NOW.plus(Duration.ofHours(5)));
        stub(b);

        BookingItem item = service.cancelAsClient(BOOKING_ID, "  No puedo asistir  ", CLIENT, "10.0.0.1");

        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELADA);
        assertThat(b.getCancelOrigin()).isEqualTo(CancelOrigin.CLIENTE);
        assertThat(b.getCancelReason()).isEqualTo("No puedo asistir");
        assertThat(b.getCancelledAt()).isEqualTo(NOW);
        assertThat(item.status()).isEqualTo("CANCELADA");
        assertThat(item.cancelOrigin()).isEqualTo("CLIENTE");
        assertThat(item.cancelReason()).isEqualTo("No puedo asistir");
        verify(bookingRepository).saveAndFlush(b);
        verify(auditService).registerEvent(eq(AuditEventType.CANCELACION_RESERVA), eq("cliente@example.com"),
                eq("SUCCESS"), anyString(), eq("10.0.0.1"));
    }

    @Test
    void elMotivoEsOpcionalYUnoEnBlancoSeGuardaComoAusente() {
        Booking b = booking(CLIENT.id(), BookingStatus.CONFIRMADA, NOW.plus(Duration.ofHours(5)));
        stub(b);
        service.cancelAsClient(BOOKING_ID, null, CLIENT, "ip");
        assertThat(b.getCancelReason()).isNull();

        Booking c = booking(CLIENT.id(), BookingStatus.CONFIRMADA, NOW.plus(Duration.ofHours(5)));
        stub(c);
        service.cancelAsClient(BOOKING_ID, "   ", CLIENT, "ip");
        assertThat(c.getCancelReason()).isNull();
    }

    @Test
    void conExactamenteUnaHoraDeAntelacionSiSePuedeCancelar() {
        Booking b = booking(CLIENT.id(), BookingStatus.CONFIRMADA, NOW.plus(Duration.ofHours(1)));
        stub(b);

        assertThat(service.cancelAsClient(BOOKING_ID, null, CLIENT, "ip").status()).isEqualTo("CANCELADA");
    }

    @Test
    void conMenosDeUnaHoraDeAntelacionOYaIniciadaNoSePuedeCancelar() {
        for (Duration before : new Duration[]{Duration.ofMinutes(59), Duration.ofMinutes(1), Duration.ZERO, Duration.ofHours(-2)}) {
            Booking b = booking(CLIENT.id(), BookingStatus.CONFIRMADA, NOW.plus(before));
            stub(b);

            assertThatThrownBy(() -> service.cancelAsClient(BOOKING_ID, null, CLIENT, "ip"))
                    .as(before.toString()).isInstanceOf(BookingNotCancellableException.class)
                    .hasMessage(BookingCancellationServiceImpl.TOO_LATE_MESSAGE);
            assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
        }
        verify(bookingRepository, never()).saveAndFlush(any());
        verify(auditService, never()).registerEvent(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unaReservaYaCanceladaOCompletadaDa409ConSuMensaje() {
        stub(booking(CLIENT.id(), BookingStatus.CANCELADA, NOW.plus(Duration.ofHours(5))));
        assertThatThrownBy(() -> service.cancelAsClient(BOOKING_ID, null, CLIENT, "ip"))
                .isInstanceOf(BookingNotCancellableException.class).hasMessage(BookingCancellationServiceImpl.ALREADY_CANCELLED_MESSAGE);

        stub(booking(CLIENT.id(), BookingStatus.COMPLETADA, NOW.plus(Duration.ofHours(5))));
        assertThatThrownBy(() -> service.cancelAsClient(BOOKING_ID, null, CLIENT, "ip"))
                .isInstanceOf(BookingNotCancellableException.class).hasMessage(BookingCancellationServiceImpl.COMPLETED_MESSAGE);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void unaReservaDeOtroClienteODeUnClienteEliminadoSeDeniega() {
        Booking other = booking(UUID.randomUUID(), BookingStatus.CONFIRMADA, NOW.plus(Duration.ofHours(5)));
        stub(other);
        assertThatThrownBy(() -> service.cancelAsClient(BOOKING_ID, null, CLIENT, "ip")).isInstanceOf(AccessDeniedException.class);
        assertThat(other.getStatus()).isEqualTo(BookingStatus.CONFIRMADA);

        stub(booking(null, BookingStatus.CONFIRMADA, NOW.plus(Duration.ofHours(5))));
        assertThatThrownBy(() -> service.cancelAsClient(BOOKING_ID, null, CLIENT, "ip")).isInstanceOf(AccessDeniedException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void unaReservaInexistenteDa404() {
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelAsClient(BOOKING_ID, null, CLIENT, "ip"))
                .isInstanceOf(BookingNotFoundException.class);
    }
}
