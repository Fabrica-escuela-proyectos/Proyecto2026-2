package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.common.error.InvalidPaginationException;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
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
    @Mock
    private BusinessAccessService businessAccessService;

    private BookingQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookingQueryServiceImpl(repository, businessAccessService);
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

    // ---------- HU-24: reservas del negocio ----------

    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final UserIdentity PROVIDER = new UserIdentity(UUID.randomUUID(), "proveedor@example.com", "PROVEEDOR");

    @SuppressWarnings("unchecked")
    private void stubBusinessPage(Page<Booking> page) {
        when(repository.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Booking>>any(),
                any(Pageable.class))).thenReturn(page);
    }

    @Test
    void listForBusinessDebeVerificarQueElProveedorEsElDuenioYMostrarElNombreDelCliente() {
        Booking b = booking(BookingStatus.CONFIRMADA);
        b.setClientName("Ana Cliente");
        b.setClientEmail("ana@example.com");
        stubBusinessPage(new PageImpl<>(List.of(b), PageRequest.of(0, 20), 1));

        var response = service.listForBusiness(BUSINESS_ID, PROVIDER, null, null, null, 0, 20);

        verify(businessAccessService).requireOwner(BUSINESS_ID, PROVIDER);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).clientName()).isEqualTo("Ana Cliente");
        assertThat(response.items().get(0).clientEmail()).isEqualTo("ana@example.com");
        assertThat(response.items().get(0).clientId()).isEqualTo(CLIENT.id());
        assertThat(response.items().get(0).date()).isEqualTo("2026-10-19");
        assertThat(response.items().get(0).startTime()).isEqualTo("10:00");
        assertThat(response.message()).isNull();
    }

    @Test
    void listForBusinessNoDebeExponerElCelularPorqueLaReservaNoLoGuarda() {
        assertThat(java.util.Arrays.stream(com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BusinessBookingItem.class
                .getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
                .contains("clientId", "clientName", "clientEmail").doesNotContain("clientPhone", "cellphone");
    }

    @Test
    void listForBusinessOrdenaDeLaMasRecienteALaMasAntiguaYRecortaElTamano() {
        stubBusinessPage(new PageImpl<>(List.of(), PageRequest.of(0, BookingQueryService.MAX_SIZE), 2));

        service.listForBusiness(BUSINESS_ID, PROVIDER, null, null, null, 0, 5000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Booking>>any(),
                pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(BookingQueryService.MAX_SIZE);
        assertThat(pageable.getValue().getSort().getOrderFor("startAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void listForBusinessSinReservasInformaQueNoHayReservasRegistradas() {
        stubBusinessPage(Page.empty(PageRequest.of(0, 20)));

        var response = service.listForBusiness(BUSINESS_ID, PROVIDER, null, null, null, 0, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.message()).isEqualTo(BookingQueryServiceImpl.NO_BUSINESS_BOOKINGS_MESSAGE);
    }

    @Test
    void listForBusinessConFiltrosSinResultadosMencionaLosFiltros() {
        stubBusinessPage(Page.empty(PageRequest.of(0, 20)));

        assertThat(service.listForBusiness(BUSINESS_ID, PROVIDER, "2026-10-01", null, null, 0, 20).message())
                .isEqualTo(BookingQueryServiceImpl.NO_BUSINESS_BOOKINGS_FILTERED_MESSAGE);
        assertThat(service.listForBusiness(BUSINESS_ID, PROVIDER, null, "2026-10-31", null, 0, 20).message())
                .isEqualTo(BookingQueryServiceImpl.NO_BUSINESS_BOOKINGS_FILTERED_MESSAGE);
        assertThat(service.listForBusiness(BUSINESS_ID, PROVIDER, null, null, "cancelada", 0, 20).message())
                .isEqualTo(BookingQueryServiceImpl.NO_BUSINESS_BOOKINGS_FILTERED_MESSAGE);
    }

    @Test
    void listForBusinessRechazaFechasInvalidasRangoInvertidoYEstadoInvalido() {
        for (String bad : new String[]{"32/13/2026", "2026-99-99", "hoy", "2026-1-5"}) {
            assertThatThrownBy(() -> service.listForBusiness(BUSINESS_ID, PROVIDER, bad, null, null, 0, 20))
                    .as(bad).isInstanceOf(InvalidBookingException.class).hasMessage(BookingQueryServiceImpl.INVALID_DATE_MESSAGE);
            assertThatThrownBy(() -> service.listForBusiness(BUSINESS_ID, PROVIDER, null, bad, null, 0, 20))
                    .as(bad).isInstanceOf(InvalidBookingException.class);
        }
        assertThatThrownBy(() -> service.listForBusiness(BUSINESS_ID, PROVIDER, "2026-10-20", "2026-10-19", null, 0, 20))
                .isInstanceOf(InvalidBookingException.class).hasMessage(BookingQueryServiceImpl.INVALID_DATE_RANGE_MESSAGE);
        assertThatThrownBy(() -> service.listForBusiness(BUSINESS_ID, PROVIDER, null, null, "PENDIENTE", 0, 20))
                .isInstanceOf(InvalidBookingException.class).hasMessage(BookingQueryServiceImpl.INVALID_STATUS_MESSAGE);
        assertThatThrownBy(() -> service.listForBusiness(BUSINESS_ID, PROVIDER, null, null, null, -1, 20))
                .isInstanceOf(InvalidPaginationException.class);
        assertThatThrownBy(() -> service.listForBusiness(BUSINESS_ID, PROVIDER, null, null, null, 0, 0))
                .isInstanceOf(InvalidPaginationException.class);
    }

    @Test
    void listForBusinessNoConsultaNadaSiElNegocioEsAjeno() {
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException("ajeno"))
                .when(businessAccessService).requireOwner(BUSINESS_ID, PROVIDER);

        assertThatThrownBy(() -> service.listForBusiness(BUSINESS_ID, PROVIDER, null, null, null, 0, 20))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(repository, never()).findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Booking>>any(),
                any(Pageable.class));
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
