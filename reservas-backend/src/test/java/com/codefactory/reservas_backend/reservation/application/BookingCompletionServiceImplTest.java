package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingNotFoundException;
import com.codefactory.reservas_backend.reservation.domain.BookingStateConflictException;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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
class BookingCompletionServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-16T20:00:00Z");
    private static final UUID BUSINESS = UUID.randomUUID();
    private static final UserIdentity PROVIDER = new UserIdentity(UUID.randomUUID(), "p@example.com", "PROVEEDOR");

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BusinessAccessService businessAccessService;
    @Mock
    private AuditService auditService;

    private BookingCompletionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookingCompletionServiceImpl(bookingRepository, businessAccessService, auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Booking booking(BookingStatus status, Instant end, UUID businessId) {
        Booking b = Booking.builder().id(UUID.randomUUID()).businessId(businessId).clientEmail("c@example.com")
                .clientName("C").businessName("N").serviceName("S").resourceName("R")
                .startAt(end.minusSeconds(3600)).endAt(end).status(status).priceCop(1).build();
        when(bookingRepository.findByIdForUpdate(b.getId())).thenReturn(Optional.of(b));
        return b;
    }

    @Test
    void completaUnaReservaConfirmadaQueYaTerminoYAudita() {
        Booking b = booking(BookingStatus.CONFIRMADA, NOW.minusSeconds(60), BUSINESS);

        var item = service.complete(b.getId(), PROVIDER, "1.2.3.4");

        assertThat(item.status()).isEqualTo("COMPLETADA");
        verify(bookingRepository).saveAndFlush(b);
        verify(auditService).registerEvent(eq(AuditEventType.FINALIZACION_RESERVA), eq("p@example.com"), eq("SUCCESS"),
                anyString(), eq("1.2.3.4"));
    }

    @Test
    void justoEnSuHoraDeFinYaSePuedeCompletar() {
        Booking b = booking(BookingStatus.CONFIRMADA, NOW, BUSINESS);

        assertThat(service.complete(b.getId(), PROVIDER, "ip").status()).isEqualTo("COMPLETADA");
    }

    @Test
    void unaReservaQueAunNoTerminaDa409SinGuardar() {
        Booking b = booking(BookingStatus.CONFIRMADA, NOW.plusSeconds(1), BUSINESS);

        assertThatThrownBy(() -> service.complete(b.getId(), PROVIDER, "ip"))
                .isInstanceOf(BookingStateConflictException.class).hasMessageContaining("haya terminado");
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void canceladaOYaCompletadaDan409() {
        Booking cancelled = booking(BookingStatus.CANCELADA, NOW.minusSeconds(60), BUSINESS);
        Booking done = booking(BookingStatus.COMPLETADA, NOW.minusSeconds(60), BUSINESS);

        assertThatThrownBy(() -> service.complete(cancelled.getId(), PROVIDER, "ip")).hasMessageContaining("cancelada");
        assertThatThrownBy(() -> service.complete(done.getId(), PROVIDER, "ip")).hasMessageContaining("ya está completada");
    }

    @Test
    void unaReservaSinNegocioOAjenaSeDeniega() {
        Booking orphan = booking(BookingStatus.CONFIRMADA, NOW.minusSeconds(60), null);
        Booking foreign = booking(BookingStatus.CONFIRMADA, NOW.minusSeconds(60), BUSINESS);
        doThrow(new AccessDeniedException("no")).when(businessAccessService).requireOwner(BUSINESS, PROVIDER);

        assertThatThrownBy(() -> service.complete(orphan.getId(), PROVIDER, "ip")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.complete(foreign.getId(), PROVIDER, "ip")).isInstanceOf(AccessDeniedException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void unaReservaInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(bookingRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(id, PROVIDER, "ip")).isInstanceOf(BookingNotFoundException.class);
    }
}
