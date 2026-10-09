package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.provider.application.BusinessDirectoryService;
import com.codefactory.reservas_backend.provider.application.BusinessSettingsService;
import com.codefactory.reservas_backend.resource.application.ResourceInfo;
import com.codefactory.reservas_backend.resource.application.ResourceLookupService;
import com.codefactory.reservas_backend.resource.application.ResourceScheduleLookup;
import com.codefactory.reservas_backend.resource.application.TimeWindow;
import com.codefactory.reservas_backend.service.controller.dto.ServiceAvailabilityDtos.ServiceAvailabilityResponse;
import com.codefactory.reservas_backend.service.domain.InvalidAvailabilityQueryException;
import com.codefactory.reservas_backend.service.domain.ServiceNotAvailableException;
import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.domain.ServiceResource;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import com.codefactory.reservas_backend.service.infrastructure.ServiceResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceAvailabilityServiceImplTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    // Miércoles 14 de octubre de 2026, 08:00 en Bogotá.
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 8, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 10, 19);

    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final UUID R1 = UUID.randomUUID();
    private static final UUID R2 = UUID.randomUUID();

    @Mock
    private ServiceOfferingRepository serviceRepository;
    @Mock
    private ServiceResourceRepository assignmentRepository;
    @Mock
    private ResourceLookupService resourceLookupService;
    @Mock
    private ResourceScheduleLookup scheduleLookup;
    @Mock
    private BusinessDirectoryService businessDirectoryService;
    @Mock
    private BusinessSettingsService businessSettingsService;
    @Mock
    private ObjectProvider<BusyTimeSource> busySources;

    private ServiceAvailabilityServiceImpl engine;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(BOGOTA).toInstant(), BOGOTA);
        engine = new ServiceAvailabilityServiceImpl(serviceRepository, assignmentRepository, resourceLookupService,
                scheduleLookup, businessDirectoryService, businessSettingsService, busySources, clock);
        lenient().when(busySources.orderedStream()).thenAnswer(inv -> Stream.<BusyTimeSource>empty());
        lenient().when(businessDirectoryService.isOwnerEnabled(BUSINESS_ID)).thenReturn(true);
        lenient().when(businessSettingsService.minAdvanceHoursOf(BUSINESS_ID)).thenReturn(1);
    }

    private void service(int duration, boolean active) {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(ServiceOffering.builder()
                .id(SERVICE_ID).businessId(BUSINESS_ID).name("Corte").durationMinutes(duration).active(active).build()));
    }

    private void resources(ResourceInfo... infos) {
        List<ServiceResource> assigned = java.util.Arrays.stream(infos).map(i -> new ServiceResource(SERVICE_ID, i.id())).toList();
        when(assignmentRepository.findByIdServiceId(SERVICE_ID)).thenReturn(assigned);
        when(resourceLookupService.findByIds(any(Collection.class))).thenReturn(List.of(infos));
    }

    private static ResourceInfo resource(UUID id, String name, boolean active) {
        return new ResourceInfo(id, BUSINESS_ID, name, "SALA", active);
    }

    private void windows(Map<UUID, List<TimeWindow>> windows, DayOfWeek day) {
        when(scheduleLookup.windowsOn(any(Collection.class), org.mockito.ArgumentMatchers.eq(day))).thenReturn(windows);
    }

    private static TimeWindow w(int h1, int m1, int h2, int m2) {
        return new TimeWindow(LocalTime.of(h1, m1), LocalTime.of(h2, m2));
    }

    private static List<String> starts(ServiceAvailabilityResponse r) {
        return r.slots().stream().map(s -> s.start()).toList();
    }

    @Test
    void debeGenerarHorariosLibresEncadenadosDeLaDuracionDelServicio() {
        service(60, true);
        resources(resource(R1, "Sala 1", true));
        windows(Map.of(R1, List.of(w(9, 0, 12, 0))), DayOfWeek.MONDAY);

        ServiceAvailabilityResponse r = engine.getAvailability(SERVICE_ID, NEXT_MONDAY.toString());

        assertThat(starts(r)).containsExactly("09:00", "10:00", "11:00");
        assertThat(r.slots().get(2).end()).isEqualTo("12:00");
        assertThat(r.message()).isNull();
        assertThat(r.date()).isEqualTo("2026-10-19");
        assertThat(r.timezone()).isEqualTo("America/Bogota");
        assertThat(r.slots().get(0).resources()).extracting("name").containsExactly("Sala 1");
    }

    @Test
    void conUnaDuracionQueNoDivideElRangoSoloEntranLosHorariosCompletos() {
        service(45, true);
        resources(resource(R1, "Sala 1", true));
        windows(Map.of(R1, List.of(w(9, 0, 12, 0))), DayOfWeek.MONDAY);

        assertThat(starts(engine.getAvailability(SERVICE_ID, NEXT_MONDAY.toString())))
                .containsExactly("09:00", "09:45", "10:30", "11:15");
    }

    @Test
    void unRangoMasCortoQueLaDuracionNoDaHorarios() {
        service(90, true);
        resources(resource(R1, "Sala 1", true));
        windows(Map.of(R1, List.of(w(9, 0, 10, 0))), DayOfWeek.MONDAY);

        ServiceAvailabilityResponse r = engine.getAvailability(SERVICE_ID, NEXT_MONDAY.toString());

        assertThat(r.slots()).isEmpty();
        assertThat(r.message()).isEqualTo(ServiceAvailabilityServiceImpl.NO_SLOTS_LEFT_MESSAGE);
    }

    @Test
    void unaDuracionDeUnDiaCompletoNoDebeColgarElMotor() {
        service(1440, true);
        resources(resource(R1, "Sala 1", true));
        windows(Map.of(R1, List.of(w(0, 0, 23, 59))), DayOfWeek.MONDAY);

        assertThat(engine.getAvailability(SERVICE_ID, NEXT_MONDAY.toString()).slots()).isEmpty();
    }

    @Test
    void dosRangosDeUnMismoDiaGeneranHorariosEnAmbos() {
        service(60, true);
        resources(resource(R1, "Sala 1", true));
        windows(Map.of(R1, List.of(w(9, 0, 10, 0), w(14, 0, 16, 0))), DayOfWeek.MONDAY);

        assertThat(starts(engine.getAvailability(SERVICE_ID, NEXT_MONDAY.toString())))
                .containsExactly("09:00", "14:00", "15:00");
    }

    @Test
    void elMismoHorarioEnVariosRecursosSeAgrupaConLaListaDeRecursos() {
        service(60, true);
        resources(resource(R1, "Sala B", true), resource(R2, "Sala A", true));
        windows(Map.of(R1, List.of(w(9, 0, 10, 0)), R2, List.of(w(9, 0, 11, 0))), DayOfWeek.MONDAY);

        ServiceAvailabilityResponse r = engine.getAvailability(SERVICE_ID, NEXT_MONDAY.toString());

        assertThat(starts(r)).containsExactly("09:00", "10:00");
        assertThat(r.slots().get(0).resources()).extracting("name").containsExactly("Sala A", "Sala B");
        assertThat(r.slots().get(1).resources()).extracting("name").containsExactly("Sala A");
    }

    @Test
    void losRecursosInactivosNoCuentan() {
        service(60, true);
        resources(resource(R1, "Activa", true), resource(R2, "Inactiva", false));
        windows(Map.of(R1, List.of(w(9, 0, 10, 0))), DayOfWeek.MONDAY);

        ServiceAvailabilityResponse r = engine.getAvailability(SERVICE_ID, NEXT_MONDAY.toString());

        assertThat(r.slots().get(0).resources()).extracting("name").containsExactly("Activa");
        verify(scheduleLookup).windowsOn(List.of(R1), DayOfWeek.MONDAY);
    }

    @Test
    void losHorariosOcupadosNoSeMuestranComoLibres() {
        service(60, true);
        resources(resource(R1, "Sala 1", true));
        windows(Map.of(R1, List.of(w(9, 0, 12, 0))), DayOfWeek.MONDAY);
        BusyTimeSource source = (ids, date) -> List.of(new BusyTimeSource.BusyInterval(
                R1, NEXT_MONDAY.atTime(10, 0), NEXT_MONDAY.atTime(11, 0)));
        when(busySources.orderedStream()).thenAnswer(inv -> Stream.of(source));

        assertThat(starts(engine.getAvailability(SERVICE_ID, NEXT_MONDAY.toString()))).containsExactly("09:00", "11:00");
    }

    @Test
    void unaOcupacionParcialQuitaTodosLosHorariosQueLaTocan() {
        service(60, true);
        resources(resource(R1, "Sala 1", true));
        windows(Map.of(R1, List.of(w(9, 0, 12, 0))), DayOfWeek.MONDAY);
        // 09:30-10:30 toca los horarios de 09:00 y de 10:00.
        BusyTimeSource source = (ids, date) -> List.of(new BusyTimeSource.BusyInterval(
                R1, NEXT_MONDAY.atTime(9, 30), NEXT_MONDAY.atTime(10, 30)));
        when(busySources.orderedStream()).thenAnswer(inv -> Stream.of(source));

        assertThat(starts(engine.getAvailability(SERVICE_ID, NEXT_MONDAY.toString()))).containsExactly("11:00");
    }

    @Test
    void laOcupacionDeUnRecursoNoQuitaElHorarioDeOtro() {
        service(60, true);
        resources(resource(R1, "Sala 1", true), resource(R2, "Sala 2", true));
        windows(Map.of(R1, List.of(w(9, 0, 10, 0)), R2, List.of(w(9, 0, 10, 0))), DayOfWeek.MONDAY);
        BusyTimeSource source = (ids, date) -> List.of(new BusyTimeSource.BusyInterval(
                R1, NEXT_MONDAY.atTime(9, 0), NEXT_MONDAY.atTime(10, 0)));
        when(busySources.orderedStream()).thenAnswer(inv -> Stream.of(source));

        ServiceAvailabilityResponse r = engine.getAvailability(SERVICE_ID, NEXT_MONDAY.toString());

        assertThat(r.slots().get(0).resources()).extracting("name").containsExactly("Sala 2");
    }

    @Test
    void todoReservadoInformaQueNoQuedanHorarios() {
        service(60, true);
        resources(resource(R1, "Sala 1", true));
        windows(Map.of(R1, List.of(w(9, 0, 10, 0))), DayOfWeek.MONDAY);
        BusyTimeSource source = (ids, date) -> List.of(new BusyTimeSource.BusyInterval(
                R1, NEXT_MONDAY.atTime(9, 0), NEXT_MONDAY.atTime(10, 0)));
        when(busySources.orderedStream()).thenAnswer(inv -> Stream.of(source));

        ServiceAvailabilityResponse r = engine.getAvailability(SERVICE_ID, NEXT_MONDAY.toString());

        assertThat(r.slots()).isEmpty();
        assertThat(r.message()).isEqualTo(ServiceAvailabilityServiceImpl.NO_SLOTS_LEFT_MESSAGE);
    }

    @Test
    void unDiaSinHorarioDefinidoInformaQueNoHayHorarios() {
        service(60, true);
        resources(resource(R1, "Sala 1", true));
        windows(Map.of(), DayOfWeek.SUNDAY);

        ServiceAvailabilityResponse r = engine.getAvailability(SERVICE_ID, "2026-10-18");

        assertThat(r.slots()).isEmpty();
        assertThat(r.message()).isEqualTo(ServiceAvailabilityServiceImpl.NO_SCHEDULE_MESSAGE);
    }

    @Test
    void unServicioSinRecursosAsignadosInformaQueNoHayHorarios() {
        service(60, true);
        when(assignmentRepository.findByIdServiceId(SERVICE_ID)).thenReturn(List.of());
        when(resourceLookupService.findByIds(any(Collection.class))).thenReturn(List.of());
        when(scheduleLookup.windowsOn(any(Collection.class), any(DayOfWeek.class))).thenReturn(Map.of());

        ServiceAvailabilityResponse r = engine.getAvailability(SERVICE_ID, NEXT_MONDAY.toString());

        assertThat(r.slots()).isEmpty();
        assertThat(r.message()).isEqualTo(ServiceAvailabilityServiceImpl.NO_SCHEDULE_MESSAGE);
    }

    @Test
    void hoySeDescuentaLaAntelacionMinimaDesdeLaHoraActual() {
        service(60, true);
        resources(resource(R1, "Sala 1", true));
        windows(Map.of(R1, List.of(w(7, 0, 12, 0))), DayOfWeek.WEDNESDAY);
        when(businessSettingsService.minAdvanceHoursOf(BUSINESS_ID)).thenReturn(2);

        // Son las 08:00 y la antelación es de 2 h: el primer horario posible es el de las 10:00.
        assertThat(starts(engine.getAvailability(SERVICE_ID, null))).containsExactly("10:00", "11:00");
    }

    @Test
    void unHorarioQueEmpiezaJustoEnElLimiteDeLaAntelacionSiSeMuestra() {
        service(60, true);
        resources(resource(R1, "Sala 1", true));
        windows(Map.of(R1, List.of(w(9, 0, 11, 0))), DayOfWeek.WEDNESDAY);

        // 08:00 + 1 h = 09:00: ese horario cumple la antelación exacta.
        assertThat(starts(engine.getAvailability(SERVICE_ID, TODAY.toString()))).containsExactly("09:00", "10:00");
    }

    @Test
    void sinFechaSeConsultaHoy() {
        service(60, true);
        resources(resource(R1, "Sala 1", true));
        windows(Map.of(R1, List.of(w(14, 0, 15, 0))), DayOfWeek.WEDNESDAY);

        ServiceAvailabilityResponse r = engine.getAvailability(SERVICE_ID, "  ");

        assertThat(r.date()).isEqualTo("2026-10-14");
        assertThat(starts(r)).containsExactly("14:00");
    }

    @Test
    void lasFechasInvalidasSeRechazanSinConsultarNada() {
        for (String bad : new String[]{"32/13/2026", "ab/cd/efgh", "2026-99-99", "2026-02-30", "2026-1-5", "hoy"}) {
            assertThatThrownBy(() -> engine.getAvailability(SERVICE_ID, bad))
                    .as(bad).isInstanceOf(InvalidAvailabilityQueryException.class)
                    .hasMessage(ServiceAvailabilityServiceImpl.INVALID_DATE_MESSAGE);
        }
    }

    @Test
    void unaFechaPasadaSeRechaza() {
        assertThatThrownBy(() -> engine.getAvailability(SERVICE_ID, "2026-10-13"))
                .isInstanceOf(InvalidAvailabilityQueryException.class)
                .hasMessage(ServiceAvailabilityServiceImpl.PAST_DATE_MESSAGE);
    }

    @Test
    void unaFechaDemasiadoLejanaSeRechazaPeroElLimiteSeAcepta() {
        assertThatThrownBy(() -> engine.getAvailability(SERVICE_ID, TODAY.plusDays(366).toString()))
                .isInstanceOf(InvalidAvailabilityQueryException.class)
                .hasMessage(ServiceAvailabilityServiceImpl.TOO_FAR_MESSAGE);

        service(60, true);
        resources(resource(R1, "Sala 1", true));
        windows(Map.of(), TODAY.plusDays(365).getDayOfWeek());
        assertThat(engine.getAvailability(SERVICE_ID, TODAY.plusDays(365).toString()).slots()).isEmpty();
    }

    @Test
    void unServicioInexistenteNoEstaDisponible() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> engine.getAvailability(SERVICE_ID, null))
                .isInstanceOf(ServiceNotAvailableException.class)
                .hasMessage(ServiceAvailabilityServiceImpl.NOT_AVAILABLE_MESSAGE);
    }

    @Test
    void unServicioInactivoNoEstaDisponible() {
        service(60, false);

        assertThatThrownBy(() -> engine.getAvailability(SERVICE_ID, null)).isInstanceOf(ServiceNotAvailableException.class);
    }

    @Test
    void elServicioDeUnProveedorInactivoNoEstaDisponible() {
        service(60, true);
        when(businessDirectoryService.isOwnerEnabled(BUSINESS_ID)).thenReturn(false);

        assertThatThrownBy(() -> engine.getAvailability(SERVICE_ID, null)).isInstanceOf(ServiceNotAvailableException.class);
    }

    @Test
    void elRelojDeNegocioUsaLaZonaDeBogotaNoLaDelServidor() {
        // 03:00 UTC del 15 de octubre = 22:00 del 14 en Bogotá: "hoy" sigue siendo el 14.
        Clock lateClock = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), BOGOTA);
        ServiceAvailabilityServiceImpl lateEngine = new ServiceAvailabilityServiceImpl(serviceRepository, assignmentRepository,
                resourceLookupService, scheduleLookup, businessDirectoryService, businessSettingsService, busySources, lateClock);
        service(60, true);
        resources(resource(R1, "Sala 1", true));
        windows(Map.of(), DayOfWeek.WEDNESDAY);

        assertThat(lateEngine.getAvailability(SERVICE_ID, null).date()).isEqualTo("2026-10-14");
    }
}
