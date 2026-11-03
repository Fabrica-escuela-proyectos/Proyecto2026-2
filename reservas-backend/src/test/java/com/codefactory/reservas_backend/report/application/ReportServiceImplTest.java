package com.codefactory.reservas_backend.report.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.provider.application.BusinessDirectoryService;
import com.codefactory.reservas_backend.report.domain.InvalidReportPeriodException;
import com.codefactory.reservas_backend.reservation.application.BookingReportQuery;
import com.codefactory.reservas_backend.reservation.application.BookingReportQuery.OccupiedMinutes;
import com.codefactory.reservas_backend.reservation.application.BookingReportQuery.ServiceCount;
import com.codefactory.reservas_backend.resource.application.ResourceInfo;
import com.codefactory.reservas_backend.resource.application.ResourceLookupService;
import com.codefactory.reservas_backend.resource.application.ResourceScheduleLookup;
import com.codefactory.reservas_backend.service.application.ServiceInfo;
import com.codefactory.reservas_backend.service.application.ServiceLookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    private static final UUID BUSINESS = UUID.randomUUID();
    private static final UUID R1 = UUID.randomUUID();
    private static final UUID R2 = UUID.randomUUID();
    private static final UUID INACTIVE = UUID.randomUUID();
    private static final LocalDate MON = LocalDate.of(2026, 11, 16);
    private static final ReportPeriod WEEK = new ReportPeriod(MON, MON.plusDays(6));
    private static final UserIdentity PROVIDER = new UserIdentity(UUID.randomUUID(), "p@example.com", "PROVEEDOR");

    @Mock
    private BusinessAccessService businessAccessService;
    @Mock
    private BusinessDirectoryService businessDirectoryService;
    @Mock
    private BookingReportQuery bookingReportQuery;
    @Mock
    private ResourceLookupService resourceLookupService;
    @Mock
    private ResourceScheduleLookup scheduleLookup;
    @Mock
    private ServiceLookupService serviceLookupService;
    @InjectMocks
    private ReportServiceImpl service;

    private void twoActiveResourcesAndOneInactive() {
        when(resourceLookupService.findAllOfBusiness(BUSINESS)).thenReturn(List.of(
                new ResourceInfo(R1, BUSINESS, "Sala A", "SALA", true),
                new ResourceInfo(R2, BUSINESS, "Sala B", "SALA", true),
                new ResourceInfo(INACTIVE, BUSINESS, "Vieja", "SALA", false)));
        when(scheduleLookup.availableMinutesByDay(List.of(R1, R2), MON, MON.plusDays(6))).thenReturn(Map.of(
                R1, Map.of(MON, 600L, MON.plusDays(1), 600L),
                R2, Map.of(MON, 300L)));
        when(bookingReportQuery.occupiedMinutesByDay(eq(BUSINESS), any(), any())).thenReturn(List.of(
                new OccupiedMinutes(R1, MON, 300L),
                new OccupiedMinutes(R2, MON, 30L),
                new OccupiedMinutes(INACTIVE, MON, 600L)));
    }

    @Test
    void ocupacionPorRecursoOrdenaDeMasAMenosUsadoEIgnoraLosInactivos() {
        twoActiveResourcesAndOneInactive();

        var report = service.resourceOccupancy(BUSINESS, WEEK, PROVIDER);

        assertThat(report.resources()).extracting(r -> r.resourceId()).containsExactly(R1, R2);
        assertThat(report.resources().get(0).occupancyPercent()).isEqualTo(25.0);
        assertThat(report.resources().get(1).occupancyPercent()).isEqualTo(10.0);
        assertThat(report.availableMinutes()).isEqualTo(1500);
        assertThat(report.bookedMinutes()).isEqualTo(330);
        assertThat(report.occupancyPercent()).isEqualTo(22.0);
        assertThat(report.message()).isNull();
        verify(businessAccessService).requireOwner(BUSINESS, PROVIDER);
    }

    @Test
    void ocupacionPorDiaMarcaLaMayorYLaMenorDemanda() {
        twoActiveResourcesAndOneInactive();

        var report = service.businessOccupancy(BUSINESS, WEEK, null, PROVIDER);

        assertThat(report.groupBy()).isEqualTo("DAY");
        assertThat(report.periods()).hasSize(7);
        assertThat(report.periods().get(0).occupancyPercent()).isEqualTo(36.7);
        assertThat(report.periods().get(2).occupancyPercent()).isNull();
        assertThat(report.highestDemand().start()).isEqualTo(MON);
        assertThat(report.lowestDemand().start()).isEqualTo(MON.plusDays(1));
    }

    @Test
    void ocupacionPorSemanaRecortaLasSemanasAlPeriodo() {
        ReportPeriod period = new ReportPeriod(MON.plusDays(3), MON.plusDays(9));
        when(resourceLookupService.findAllOfBusiness(BUSINESS)).thenReturn(List.of());
        when(scheduleLookup.availableMinutesByDay(List.of(), period.from(), period.to())).thenReturn(Map.of());

        var report = service.businessOccupancy(BUSINESS, period, "week", PROVIDER);

        assertThat(report.periods()).extracting(p -> p.start() + "/" + p.end())
                .containsExactly(MON.plusDays(3) + "/" + MON.plusDays(6), MON.plusDays(7) + "/" + MON.plusDays(9));
        assertThat(report.highestDemand()).isNull();
        assertThat(report.message()).isEqualTo(ReportServiceImpl.NO_SCHEDULE_MESSAGE);
    }

    @Test
    void unGroupByInvalidoDa400SinConsultar() {
        assertThatThrownBy(() -> service.businessOccupancy(BUSINESS, WEEK, "MONTH", PROVIDER))
                .isInstanceOf(InvalidReportPeriodException.class);
        verify(businessAccessService, never()).requireOwner(any(), any());
    }

    @Test
    void reservasPorEstadoSumaElTotalYTraeLosOrigenes() {
        when(bookingReportQuery.countByStatus(eq(BUSINESS), any(), any())).thenReturn(Map.of("CONFIRMADA", 4L, "CANCELADA", 2L));
        when(bookingReportQuery.cancelledByOrigin(eq(BUSINESS), any(), any())).thenReturn(Map.of("PROVEEDOR", 1L, "CLIENTE", 1L));

        var report = service.bookingsByStatus(BUSINESS, WEEK, PROVIDER);

        assertThat(report.total()).isEqualTo(6);
        assertThat(report.completed()).isZero();
        assertThat(report.cancelledByOrigin()).containsOnlyKeys("CLIENTE", "PROVEEDOR");
        assertThat(report.message()).isNull();
    }

    @Test
    void sinReservasElReporteDeEstadosTraeElMensaje() {
        when(bookingReportQuery.countByStatus(eq(BUSINESS), any(), any())).thenReturn(Map.of());
        when(bookingReportQuery.cancelledByOrigin(eq(BUSINESS), any(), any())).thenReturn(Map.of());

        assertThat(service.bookingsByStatus(BUSINESS, WEEK, PROVIDER).message()).isEqualTo(ReportServiceImpl.NO_BOOKINGS_MESSAGE);
    }

    @Test
    void elRankingUsaElNombreVigenteYMarcaLosEliminados() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        when(bookingReportQuery.countByService(eq(BUSINESS), any(), any())).thenReturn(List.of(
                new ServiceCount(s1, "Viejo", 2, 0), new ServiceCount(null, "Borrado", 5, 1), new ServiceCount(s2, "Otro", 2, 3)));
        when(serviceLookupService.findById(s1)).thenReturn(Optional.of(new ServiceInfo(s1, BUSINESS, "Nuevo", 30, 1, true)));
        when(serviceLookupService.findById(s2)).thenReturn(Optional.empty());

        var report = service.topServices(BUSINESS, WEEK, null, PROVIDER);

        assertThat(report.services()).extracting(r -> r.rank() + ":" + r.serviceName())
                .containsExactly("1:Borrado (servicio eliminado)", "2:Nuevo", "3:Otro");
    }

    @Test
    void unLimiteFueraDeRangoDa400() {
        assertThatThrownBy(() -> service.topServices(BUSINESS, WEEK, 0, PROVIDER)).isInstanceOf(InvalidReportPeriodException.class);
        assertThatThrownBy(() -> service.topServices(BUSINESS, WEEK, 51, PROVIDER)).isInstanceOf(InvalidReportPeriodException.class);
    }

    @Test
    void elValorSumaCompletadasYConfirmadasYDejaAparteLasCanceladas() {
        when(bookingReportQuery.valueByStatus(eq(BUSINESS), any(), any()))
                .thenReturn(Map.of("COMPLETADA", 40_000L, "CONFIRMADA", 70_000L, "CANCELADA", 25_000L));
        when(bookingReportQuery.countByStatus(eq(BUSINESS), any(), any())).thenReturn(Map.of("CONFIRMADA", 4L));

        var report = service.bookingValue(BUSINESS, WEEK, PROVIDER);

        assertThat(report.totalValue()).isEqualTo(110_000);
        assertThat(report.cancelledValue()).isEqualTo(25_000);
        assertThat(report.note()).contains("no corresponde a pagos");
    }

    @Test
    void unNegocioAjenoNoLlegaANingunaConsulta() {
        doThrow(new AccessDeniedException("no")).when(businessAccessService).requireOwner(BUSINESS, PROVIDER);

        assertThatThrownBy(() -> service.bookingValue(BUSINESS, WEEK, PROVIDER)).isInstanceOf(AccessDeniedException.class);
        verify(bookingReportQuery, never()).valueByStatus(any(), any(), any());
    }

    @Test
    void elResumenDeLaPlataformaConsultaSinNegocio() {
        when(bookingReportQuery.countByStatus(eq(null), any(), any())).thenReturn(Map.of("COMPLETADA", 3L));
        when(resourceLookupService.allActiveIds()).thenReturn(List.of(R1));
        when(scheduleLookup.availableMinutesByDay(anyCollection(), eq(MON), eq(MON.plusDays(6)))).thenReturn(Map.of(R1, Map.of(MON, 200L)));
        when(bookingReportQuery.occupiedMinutesByDay(eq(null), any(), any())).thenReturn(List.of(new OccupiedMinutes(R1, MON, 50L)));
        when(businessDirectoryService.countWithEnabledOwner()).thenReturn(7L);

        var report = service.platformSummary(WEEK);

        assertThat(report.activeBusinesses()).isEqualTo(7);
        assertThat(report.totalBookings()).isEqualTo(3);
        assertThat(report.occupancyPercent()).isEqualTo(25.0);
    }

    @Test
    void elPorcentajeRedondeaAUnDecimalYEsNuloSinDisponibilidad() {
        assertThat(ReportServiceImpl.percent(1, 3)).isEqualTo(33.3);
        assertThat(ReportServiceImpl.percent(2, 3)).isEqualTo(66.7);
        assertThat(ReportServiceImpl.percent(5, 0)).isNull();
    }

    // --- ReportPeriod ---

    @Test
    void elPeriodoPorDefectoSonLosUltimos30Dias() {
        ReportPeriod p = ReportPeriod.parse(null, " ", MON);

        assertThat(p.from()).isEqualTo(MON.minusDays(29));
        assertThat(p.to()).isEqualTo(MON);
    }

    @Test
    void conUnaSolaFechaLaOtraSeCompletaA30Dias() {
        assertThat(ReportPeriod.parse("2026-11-01", null, MON).to()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(ReportPeriod.parse(null, "2026-11-30", MON).from()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void periodosInvalidos() {
        assertThatThrownBy(() -> ReportPeriod.parse("2026-11-20", "2026-11-19", MON)).hasMessageContaining("posterior");
        assertThatThrownBy(() -> ReportPeriod.parse("2026/11/20", null, MON)).hasMessageContaining("yyyy-MM-dd");
        assertThatThrownBy(() -> ReportPeriod.parse("2026-01-01", "2027-01-02", MON)).hasMessageContaining("366");
        assertThat(ReportPeriod.parse("2026-01-01", "2027-01-01", MON).to()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void losInstantesDelPeriodoSonMedianocheDeBogota() {
        ReportPeriod p = new ReportPeriod(MON, MON);

        assertThat(p.start().toString()).isEqualTo("2026-11-16T05:00:00Z");
        assertThat(p.end().toString()).isEqualTo("2026-11-17T05:00:00Z");
    }
}
