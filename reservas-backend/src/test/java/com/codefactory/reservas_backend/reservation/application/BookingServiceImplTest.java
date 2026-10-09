package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessDirectoryService;
import com.codefactory.reservas_backend.provider.application.BusinessInfo;
import com.codefactory.reservas_backend.provider.application.BusinessSettingsService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingResponse;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.CreateBookingRequest;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.InvalidBookingException;
import com.codefactory.reservas_backend.reservation.domain.SlotNotAvailableException;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.resource.application.ResourceInfo;
import com.codefactory.reservas_backend.resource.application.ResourceLookupService;
import com.codefactory.reservas_backend.resource.application.ResourceScheduleLookup;
import com.codefactory.reservas_backend.resource.application.TimeWindow;
import com.codefactory.reservas_backend.service.application.ServiceInfo;
import com.codefactory.reservas_backend.service.application.ServiceLookupService;
import com.codefactory.reservas_backend.service.domain.ServiceNotAvailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    // Miércoles 14 de octubre de 2026, 08:00 en Bogotá. El lunes siguiente es el 19.
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 8, 0);
    private static final String MONDAY = "2026-10-19";

    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final UUID R1 = UUID.randomUUID();
    private static final UUID R2 = UUID.randomUUID();
    private static final UserIdentity CLIENT = new UserIdentity(UUID.randomUUID(), "cliente@example.com", "CLIENTE");

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ServiceLookupService serviceLookupService;
    @Mock
    private ResourceLookupService resourceLookupService;
    @Mock
    private ResourceScheduleLookup scheduleLookup;
    @Mock
    private BusinessDirectoryService businessDirectoryService;
    @Mock
    private BusinessSettingsService businessSettingsService;
    @Mock
    private AuditService auditService;

    private BookingServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(BOGOTA).toInstant(), BOGOTA);
        service = new BookingServiceImpl(bookingRepository, serviceLookupService, resourceLookupService, scheduleLookup,
                businessDirectoryService, businessSettingsService, auditService, clock);
        lenient().when(serviceLookupService.findById(SERVICE_ID)).thenReturn(Optional.of(
                new ServiceInfo(SERVICE_ID, BUSINESS_ID, "Corte", 60, 25_000L, true)));
        lenient().when(businessDirectoryService.isOwnerEnabled(BUSINESS_ID)).thenReturn(true);
        lenient().when(businessDirectoryService.get(BUSINESS_ID)).thenReturn(new BusinessInfo(BUSINESS_ID, "Barbería"));
        lenient().when(businessSettingsService.minAdvanceHoursOf(BUSINESS_ID)).thenReturn(1);
        lenient().when(serviceLookupService.assignedResourceIds(SERVICE_ID)).thenReturn(List.of(R1));
        lenient().when(resourceLookupService.findByIds(any(Collection.class))).thenReturn(List.of(resource(R1, "Sala 1", true)));
        lenient().when(scheduleLookup.windowsOn(any(Collection.class), eq(DayOfWeek.MONDAY)))
                .thenReturn(Map.of(R1, List.of(window(9, 18))));
        lenient().when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            b.setCreatedAt(Instant.now());
            return b;
        });
    }

    private static ResourceInfo resource(UUID id, String name, boolean active) {
        return new ResourceInfo(id, BUSINESS_ID, name, "SALA", active);
    }

    private static TimeWindow window(int startHour, int endHour) {
        return new TimeWindow(LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private static CreateBookingRequest request(String date, String start, String end, UUID resourceId) {
        CreateBookingRequest r = new CreateBookingRequest();
        r.setServiceId(SERVICE_ID);
        r.setDate(date);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setResourceId(resourceId);
        return r;
    }

    @Test
    void createDebeGuardarUnaReservaConfirmadaConSnapshotsYAuditar() {
        BookingResponse response = service.create(request(MONDAY, "10:00", "11:00", null), CLIENT, "10.0.0.1");

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).saveAndFlush(saved.capture());
        Booking b = saved.getValue();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
        assertThat(b.getClientId()).isEqualTo(CLIENT.id());
        assertThat(b.getClientEmail()).isEqualTo("cliente@example.com");
        assertThat(b.getServiceName()).isEqualTo("Corte");
        assertThat(b.getBusinessName()).isEqualTo("Barbería");
        assertThat(b.getResourceName()).isEqualTo("Sala 1");
        assertThat(b.getPriceCop()).isEqualTo(25_000L);
        // 10:00 en Bogotá (UTC-5) son las 15:00 UTC.
        assertThat(b.getStartAt()).isEqualTo(Instant.parse("2026-10-19T15:00:00Z"));
        assertThat(b.getEndAt()).isEqualTo(Instant.parse("2026-10-19T16:00:00Z"));
        assertThat(response.id()).isEqualTo(b.getId());
        assertThat(response.status()).isEqualTo("CONFIRMADA");
        assertThat(response.date()).isEqualTo(MONDAY);
        verify(auditService).registerEvent(eq(AuditEventType.CREACION_RESERVA), eq("cliente@example.com"),
                eq("SUCCESS"), anyString(), eq("10.0.0.1"));
    }

    @Test
    void sinRecursoIndicadoElegiElPrimeroLibrePorNombre() {
        when(serviceLookupService.assignedResourceIds(SERVICE_ID)).thenReturn(List.of(R1, R2));
        when(resourceLookupService.findByIds(any(Collection.class))).thenReturn(List.of(resource(R1, "Sala B", true), resource(R2, "Sala A", true)));
        when(scheduleLookup.windowsOn(any(Collection.class), eq(DayOfWeek.MONDAY)))
                .thenReturn(Map.of(R1, List.of(window(9, 18)), R2, List.of(window(9, 18))));

        BookingResponse response = service.create(request(MONDAY, "10:00", "11:00", null), CLIENT, "ip");

        assertThat(response.resourceName()).isEqualTo("Sala A");
        assertThat(response.resourceId()).isEqualTo(R2);
    }

    @Test
    void siElPrimeroEstaOcupadoUsaElSiguienteRecursoLibre() {
        when(serviceLookupService.assignedResourceIds(SERVICE_ID)).thenReturn(List.of(R1, R2));
        when(resourceLookupService.findByIds(any(Collection.class))).thenReturn(List.of(resource(R1, "Sala A", true), resource(R2, "Sala B", true)));
        when(scheduleLookup.windowsOn(any(Collection.class), eq(DayOfWeek.MONDAY)))
                .thenReturn(Map.of(R1, List.of(window(9, 18)), R2, List.of(window(9, 18))));
        when(bookingRepository.existsConfirmedOverlapping(eq(R1), any(), any())).thenReturn(true);
        when(bookingRepository.existsConfirmedOverlapping(eq(R2), any(), any())).thenReturn(false);

        assertThat(service.create(request(MONDAY, "10:00", "11:00", null), CLIENT, "ip").resourceName()).isEqualTo("Sala B");
    }

    @Test
    void conRecursoIndicadoSoloUsaEse() {
        when(serviceLookupService.assignedResourceIds(SERVICE_ID)).thenReturn(List.of(R1, R2));
        when(resourceLookupService.findByIds(any(Collection.class))).thenReturn(List.of(resource(R1, "Sala A", true), resource(R2, "Sala B", true)));
        when(scheduleLookup.windowsOn(any(Collection.class), eq(DayOfWeek.MONDAY)))
                .thenReturn(Map.of(R2, List.of(window(9, 18))));

        assertThat(service.create(request(MONDAY, "10:00", "11:00", R2), CLIENT, "ip").resourceId()).isEqualTo(R2);
    }

    @Test
    void unRecursoIndicadoQueNoEstaAsignadoOEstaInactivoSeRechaza() {
        when(resourceLookupService.findByIds(any(Collection.class))).thenReturn(List.of(resource(R1, "Sala 1", false)));

        assertThatThrownBy(() -> service.create(request(MONDAY, "10:00", "11:00", R1), CLIENT, "ip"))
                .isInstanceOf(InvalidBookingException.class).hasMessage(BookingServiceImpl.RESOURCE_NOT_AVAILABLE_MESSAGE);
        assertThatThrownBy(() -> service.create(request(MONDAY, "10:00", "11:00", UUID.randomUUID()), CLIENT, "ip"))
                .isInstanceOf(InvalidBookingException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void unHorarioOcupadoDa409() {
        when(bookingRepository.existsConfirmedOverlapping(eq(R1), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(MONDAY, "10:00", "11:00", null), CLIENT, "ip"))
                .isInstanceOf(SlotNotAvailableException.class).hasMessage(BookingServiceImpl.OCCUPIED_MESSAGE);
        verify(bookingRepository, never()).saveAndFlush(any());
        verify(auditService, never()).registerEvent(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unHorarioFueraDelHorarioDelRecursoDa409() {
        assertThatThrownBy(() -> service.create(request(MONDAY, "17:30", "18:30", null), CLIENT, "ip"))
                .isInstanceOf(SlotNotAvailableException.class).hasMessage(BookingServiceImpl.OUT_OF_SCHEDULE_MESSAGE);
        assertThatThrownBy(() -> service.create(request(MONDAY, "08:00", "09:00", null), CLIENT, "ip"))
                .isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void unHorarioQueTocaElBordeDelRangoSiSeAcepta() {
        assertThat(service.create(request(MONDAY, "17:00", "18:00", null), CLIENT, "ip").endTime()).isEqualTo("18:00");
        assertThat(service.create(request(MONDAY, "09:00", "10:00", null), CLIENT, "ip").startTime()).isEqualTo("09:00");
    }

    @Test
    void unDiaSinHorarioDefinidoDa409() {
        when(scheduleLookup.windowsOn(any(Collection.class), eq(DayOfWeek.MONDAY))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.create(request(MONDAY, "10:00", "11:00", null), CLIENT, "ip"))
                .isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void unServicioSinRecursosAsignadosDa409() {
        when(serviceLookupService.assignedResourceIds(SERVICE_ID)).thenReturn(List.of());
        when(resourceLookupService.findByIds(any(Collection.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request(MONDAY, "10:00", "11:00", null), CLIENT, "ip"))
                .isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void unRecursoInactivoNoSeUsaAunqueEsteAsignado() {
        when(resourceLookupService.findByIds(any(Collection.class))).thenReturn(List.of(resource(R1, "Sala 1", false)));

        assertThatThrownBy(() -> service.create(request(MONDAY, "10:00", "11:00", null), CLIENT, "ip"))
                .isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void unaHoraDeFinAnteriorOIgualALaDeInicioSeRechaza() {
        for (String[] range : new String[][]{{"11:00", "10:00"}, {"10:00", "10:00"}}) {
            assertThatThrownBy(() -> service.create(request(MONDAY, range[0], range[1], null), CLIENT, "ip"))
                    .isInstanceOf(InvalidBookingException.class).hasMessage(BookingServiceImpl.INVALID_RANGE_MESSAGE);
        }
        verify(serviceLookupService, never()).findById(any());
    }

    @Test
    void unaFechaInvalidaOPasadaSeRechaza() {
        assertThatThrownBy(() -> service.create(request("32/13/2026", "10:00", "11:00", null), CLIENT, "ip"))
                .isInstanceOf(InvalidBookingException.class).hasMessage(BookingServiceImpl.INVALID_DATE_MESSAGE);
        assertThatThrownBy(() -> service.create(request("2026-10-13", "10:00", "11:00", null), CLIENT, "ip"))
                .isInstanceOf(InvalidBookingException.class).hasMessage(BookingServiceImpl.PAST_DATE_MESSAGE);
    }

    @Test
    void laDuracionDebeCoincidirConLaDelServicio() {
        assertThatThrownBy(() -> service.create(request(MONDAY, "10:00", "11:30", null), CLIENT, "ip"))
                .isInstanceOf(InvalidBookingException.class).hasMessageContaining("60 minutos");
    }

    @Test
    void laAntelacionMinimaDelNegocioSeExige() {
        when(scheduleLookup.windowsOn(any(Collection.class), eq(DayOfWeek.WEDNESDAY))).thenReturn(Map.of(R1, List.of(window(7, 20))));
        when(businessSettingsService.minAdvanceHoursOf(BUSINESS_ID)).thenReturn(3);

        // Son las 08:00 de hoy (miércoles) y la antelación es de 3 h: 10:00 no alcanza, 11:00 sí.
        assertThatThrownBy(() -> service.create(request("2026-10-14", "10:00", "11:00", null), CLIENT, "ip"))
                .isInstanceOf(InvalidBookingException.class).hasMessageContaining("3 horas");
        assertThat(service.create(request("2026-10-14", "11:00", "12:00", null), CLIENT, "ip").startTime()).isEqualTo("11:00");
    }

    @Test
    void unaHoraDeAntelacionSeEscribeEnSingular() {
        // La antelación se valida antes de mirar horarios, así que no hace falta definir ninguno.
        assertThatThrownBy(() -> service.create(request("2026-10-14", "08:00", "09:00", null), CLIENT, "ip"))
                .isInstanceOf(InvalidBookingException.class).hasMessageContaining("1 hora ");
    }

    @Test
    void unServicioInexistenteInactivoOConProveedorInactivoNoEstaDisponible() {
        when(serviceLookupService.findById(SERVICE_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(MONDAY, "10:00", "11:00", null), CLIENT, "ip"))
                .isInstanceOf(ServiceNotAvailableException.class);

        when(serviceLookupService.findById(SERVICE_ID)).thenReturn(Optional.of(
                new ServiceInfo(SERVICE_ID, BUSINESS_ID, "Corte", 60, 25_000L, false)));
        assertThatThrownBy(() -> service.create(request(MONDAY, "10:00", "11:00", null), CLIENT, "ip"))
                .isInstanceOf(ServiceNotAvailableException.class);

        when(serviceLookupService.findById(SERVICE_ID)).thenReturn(Optional.of(
                new ServiceInfo(SERVICE_ID, BUSINESS_ID, "Corte", 60, 25_000L, true)));
        when(businessDirectoryService.isOwnerEnabled(BUSINESS_ID)).thenReturn(false);
        assertThatThrownBy(() -> service.create(request(MONDAY, "10:00", "11:00", null), CLIENT, "ip"))
                .isInstanceOf(ServiceNotAvailableException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }
}
