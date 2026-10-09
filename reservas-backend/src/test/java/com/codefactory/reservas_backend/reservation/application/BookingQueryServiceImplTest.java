package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.common.error.InvalidPaginationException;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingItem;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingPageResponse;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.InvalidBookingException;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingQueryServiceImplTest {

    private static final UserIdentity CLIENT = new UserIdentity(UUID.randomUUID(), "cliente@example.com", "CLIENTE");

    @Mock
    private BookingRepository repository;

    private BookingQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookingQueryServiceImpl(repository);
    }

    private static Booking booking(BookingStatus status) {
        return Booking.builder().id(UUID.randomUUID()).clientId(CLIENT.id()).serviceId(UUID.randomUUID()).serviceName("Corte")
                .businessId(UUID.randomUUID()).businessName("Barbería").resourceId(UUID.randomUUID()).resourceName("Sala 1")
                // 15:00Z - 16:00Z = 10:00 - 11:00 en Bogotá
                .startAt(Instant.parse("2026-10-19T15:00:00Z")).endAt(Instant.parse("2026-10-19T16:00:00Z"))
                .status(status).priceCop(25_000L).createdAt(Instant.parse("2026-10-14T13:00:00Z")).build();
    }

    @Test
    void listOwnDebeConsultarSoloLasDelClienteOrdenadasDeLaMasRecienteALaMasAntigua() {
        Booking b = booking(BookingStatus.CONFIRMADA);
        when(repository.findByClientId(eq(CLIENT.id()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(b), PageRequest.of(0, 20), 1));

        BookingPageResponse response = service.listOwn(CLIENT, null, 0, 20);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByClientId(eq(CLIENT.id()), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("startAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
        assertThat(response.items()).hasSize(1);
        BookingItem item = response.items().get(0);
        assertThat(item.date()).isEqualTo("2026-10-19");
        assertThat(item.startTime()).isEqualTo("10:00");
        assertThat(item.endTime()).isEqualTo("11:00");
        assertThat(item.serviceName()).isEqualTo("Corte");
        assertThat(item.status()).isEqualTo("CONFIRMADA");
        assertThat(item.cancelReason()).isNull();
        assertThat(response.message()).isNull();
    }

    @Test
    void listOwnDebeFiltrarPorEstadoSinDistinguirMayusculas() {
        when(repository.findByClientIdAndStatus(eq(CLIENT.id()), eq(BookingStatus.CANCELADA), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(booking(BookingStatus.CANCELADA)), PageRequest.of(0, 20), 1));

        BookingPageResponse response = service.listOwn(CLIENT, " cancelada ", 0, 20);

        assertThat(response.items().get(0).status()).isEqualTo("CANCELADA");
        verify(repository, never()).findByClientId(any(), any());
    }

    @Test
    void listOwnDebeMostrarElMotivoYLaFechaDeCancelacion() {
        Booking cancelled = booking(BookingStatus.CANCELADA);
        cancelled.setCancelReason("No puedo asistir");
        cancelled.setCancelledAt(Instant.parse("2026-10-15T12:00:00Z"));
        when(repository.findByClientId(eq(CLIENT.id()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cancelled), PageRequest.of(0, 20), 1));

        BookingItem item = service.listOwn(CLIENT, null, 0, 20).items().get(0);

        assertThat(item.cancelReason()).isEqualTo("No puedo asistir");
        assertThat(item.cancelledAt()).isEqualTo(Instant.parse("2026-10-15T12:00:00Z"));
    }

    @Test
    void sinReservasDevuelveListaVaciaYElMensaje() {
        when(repository.findByClientId(eq(CLIENT.id()), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        BookingPageResponse response = service.listOwn(CLIENT, null, 0, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.message()).isEqualTo(BookingQueryServiceImpl.NO_BOOKINGS_MESSAGE);
        assertThat(response.totalElements()).isZero();
    }

    @Test
    void sinReservasConElFiltroDeEstadoElMensajeLoMenciona() {
        when(repository.findByClientIdAndStatus(eq(CLIENT.id()), eq(BookingStatus.COMPLETADA), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        assertThat(service.listOwn(CLIENT, "COMPLETADA", 0, 20).message())
                .isEqualTo(BookingQueryServiceImpl.NO_BOOKINGS_WITH_STATUS_MESSAGE);
    }

    @Test
    void unaPaginaFueraDeRangoNoLlevaMensajeDeVacio() {
        when(repository.findByClientId(eq(CLIENT.id()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(9, 20), 3));

        assertThat(service.listOwn(CLIENT, null, 9, 20).message()).isNull();
    }

    @Test
    void elTamanoSeRecortaAlTopeMaximo() {
        when(repository.findByClientId(eq(CLIENT.id()), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, BookingQueryService.MAX_SIZE)));

        service.listOwn(CLIENT, null, 0, 5000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByClientId(eq(CLIENT.id()), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(BookingQueryService.MAX_SIZE);
    }

    @Test
    void paginacionInvalidaYEstadoInvalidoSeRechazanSinConsultarLaBase() {
        assertThatThrownBy(() -> service.listOwn(CLIENT, null, -1, 10)).isInstanceOf(InvalidPaginationException.class);
        assertThatThrownBy(() -> service.listOwn(CLIENT, null, 0, 0)).isInstanceOf(InvalidPaginationException.class);
        assertThatThrownBy(() -> service.listOwn(CLIENT, "PENDIENTE", 0, 10))
                .isInstanceOf(InvalidBookingException.class).hasMessage(BookingQueryServiceImpl.INVALID_STATUS_MESSAGE);
        verify(repository, never()).findByClientId(any(), any());
        verify(repository, never()).findByClientIdAndStatus(any(), any(), any());
    }
}
