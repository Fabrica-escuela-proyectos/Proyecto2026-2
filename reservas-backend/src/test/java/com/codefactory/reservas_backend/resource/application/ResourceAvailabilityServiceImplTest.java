package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.AvailabilityResponse;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.DayScheduleRequest;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.TimeRange;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.WeekScheduleRequest;
import com.codefactory.reservas_backend.resource.domain.InvalidAvailabilityException;
import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.domain.ResourceAvailability;
import com.codefactory.reservas_backend.resource.domain.ResourceNotFoundException;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceAvailabilityRepository;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceAvailabilityServiceImplTest {

    private static final UUID RESOURCE_ID = UUID.randomUUID();
    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final UserIdentity OWNER = new UserIdentity(UUID.randomUUID(), "dueno@example.com", "PROVEEDOR");

    @Mock
    private ResourceRepository resourceRepository;
    @Mock
    private ResourceAvailabilityRepository availabilityRepository;
    @Mock
    private BusinessAccessService businessAccessService;
    @Mock
    private AuditService auditService;

    private ResourceAvailabilityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResourceAvailabilityServiceImpl(resourceRepository, availabilityRepository,
                businessAccessService, auditService);
    }

    private void stubResource() {
        when(resourceRepository.findByIdForUpdate(RESOURCE_ID)).thenReturn(Optional.of(
                Resource.builder().id(RESOURCE_ID).businessId(BUSINESS_ID).name("Sala 1").build()));
    }

    private static TimeRange range(String start, String end) {
        return new TimeRange(start, end);
    }

    @Test
    void replaceDayDebeGuardarLosRangosYDevolverLosSieteDias() {
        stubResource();
        when(availabilityRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(RESOURCE_ID)).thenReturn(List.of(
                ResourceAvailability.builder().dayOfWeek(1).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).build()));

        AvailabilityResponse response = service.replaceDay(RESOURCE_ID, 1,
                List.of(range("14:00", "18:00"), range("09:00", "12:00")), OWNER, "10.0.0.1");

        verify(availabilityRepository).deleteDayOfResource(RESOURCE_ID, 1);
        ArgumentCaptor<List<ResourceAvailability>> saved = ArgumentCaptor.forClass(List.class);
        verify(availabilityRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(ResourceAvailability::getStartTime)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(14, 0));
        assertThat(response.days()).hasSize(7);
        assertThat(response.timezone()).isEqualTo("America/Bogota");
        assertThat(response.days().get(0).ranges()).containsExactly(range("09:00", "12:00"));
        assertThat(response.days().get(6).ranges()).isEmpty();
        verify(auditService).registerEvent(eq(AuditEventType.DISPONIBILIDAD_RECURSO), eq("dueno@example.com"),
                eq("SUCCESS"), anyString(), eq("10.0.0.1"));
    }

    @Test
    void replaceDayConListaVaciaDejaElDiaNoDisponible() {
        stubResource();
        when(availabilityRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(RESOURCE_ID)).thenReturn(List.of());

        AvailabilityResponse response = service.replaceDay(RESOURCE_ID, 7, List.of(), OWNER, "ip");

        verify(availabilityRepository).deleteDayOfResource(RESOURCE_ID, 7);
        assertThat(response.days().get(6).ranges()).isEmpty();
    }

    @Test
    void replaceDayDebeAceptarRangosConsecutivosQueSoloComparteElBorde() {
        stubResource();
        when(availabilityRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(RESOURCE_ID)).thenReturn(List.of());

        service.replaceDay(RESOURCE_ID, 2, List.of(range("09:00", "12:00"), range("12:00", "14:00")), OWNER, "ip");

        verify(availabilityRepository).saveAll(org.mockito.ArgumentMatchers.<List<ResourceAvailability>>any());
    }

    @Test
    void replaceDayDebeRechazarRangosConInicioNoAnteriorAlFinSinTocarLaBase() {
        stubResource();

        for (TimeRange invalid : List.of(range("14:00", "10:00"), range("09:00", "09:00"))) {
            assertThatThrownBy(() -> service.replaceDay(RESOURCE_ID, 1, List.of(invalid), OWNER, "ip"))
                    .isInstanceOf(InvalidAvailabilityException.class)
                    .hasMessageContaining("no es válido");
        }
        verify(availabilityRepository, never()).deleteDayOfResource(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(availabilityRepository, never()).saveAll(anyIterable());
    }

    @Test
    void replaceDayDebeRechazarRangosSuperpuestos() {
        stubResource();

        assertThatThrownBy(() -> service.replaceDay(RESOURCE_ID, 1,
                List.of(range("09:00", "12:00"), range("11:00", "13:00")), OWNER, "ip"))
                .isInstanceOf(InvalidAvailabilityException.class)
                .hasMessageContaining("superponerse");
        verify(availabilityRepository, never()).saveAll(anyIterable());
    }

    @Test
    void replaceDayDebeRechazarUnRangoContenidoEnOtro() {
        stubResource();

        assertThatThrownBy(() -> service.replaceDay(RESOURCE_ID, 1,
                List.of(range("08:00", "18:00"), range("10:00", "11:00")), OWNER, "ip"))
                .isInstanceOf(InvalidAvailabilityException.class);
    }

    @Test
    void replaceDayDebeRechazarUnDiaFueraDeRango() {
        stubResource();

        assertThatThrownBy(() -> service.replaceDay(RESOURCE_ID, 0, List.of(), OWNER, "ip"))
                .isInstanceOf(InvalidAvailabilityException.class);
        assertThatThrownBy(() -> service.replaceDay(RESOURCE_ID, 8, List.of(), OWNER, "ip"))
                .isInstanceOf(InvalidAvailabilityException.class);
    }

    @Test
    void replaceWeekDebeReemplazarTodaLaSemana() {
        stubResource();

        AvailabilityResponse response = service.replaceWeek(RESOURCE_ID, new WeekScheduleRequest(List.of(
                new DayScheduleRequest(1, List.of(range("09:00", "17:00"))),
                new DayScheduleRequest(3, List.of(range("10:00", "12:00"), range("14:00", "16:00"))))), OWNER, "ip");

        verify(availabilityRepository).deleteAllOfResource(RESOURCE_ID);
        ArgumentCaptor<List<ResourceAvailability>> saved = ArgumentCaptor.forClass(List.class);
        verify(availabilityRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(3);
        assertThat(response.days().get(0).ranges()).hasSize(1);
        assertThat(response.days().get(1).ranges()).isEmpty();
        assertThat(response.days().get(2).ranges()).hasSize(2);
    }

    @Test
    void replaceWeekDebeRechazarDiasRepetidosYNoTocarLaBase() {
        stubResource();

        assertThatThrownBy(() -> service.replaceWeek(RESOURCE_ID, new WeekScheduleRequest(List.of(
                new DayScheduleRequest(1, List.of()), new DayScheduleRequest(1, List.of()))), OWNER, "ip"))
                .isInstanceOf(InvalidAvailabilityException.class);
        verify(availabilityRepository, never()).deleteAllOfResource(any());
    }

    @Test
    void replaceWeekDebeSerAtomicaSiUnDiaTieneUnRangoInvalido() {
        stubResource();

        assertThatThrownBy(() -> service.replaceWeek(RESOURCE_ID, new WeekScheduleRequest(List.of(
                new DayScheduleRequest(1, List.of(range("09:00", "12:00"))),
                new DayScheduleRequest(2, List.of(range("12:00", "09:00"))))), OWNER, "ip"))
                .isInstanceOf(InvalidAvailabilityException.class);
        verify(availabilityRepository, never()).deleteAllOfResource(any());
        verify(availabilityRepository, never()).saveAll(anyIterable());
    }

    @Test
    void replaceDebeDenegarSiElRecursoEsDeOtroProveedor() {
        stubResource();
        doThrow(new AccessDeniedException("ajeno")).when(businessAccessService).requireOwner(BUSINESS_ID, OWNER);

        assertThatThrownBy(() -> service.replaceDay(RESOURCE_ID, 1, List.of(), OWNER, "ip"))
                .isInstanceOf(AccessDeniedException.class);
        verify(availabilityRepository, never()).deleteDayOfResource(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void replaceDebeFallarSiElRecursoNoExiste() {
        when(resourceRepository.findByIdForUpdate(RESOURCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceWeek(RESOURCE_ID, new WeekScheduleRequest(List.of()), OWNER, "ip"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getDebeDevolverLosSieteDiasConLosDiasSinRangosVacios() {
        when(resourceRepository.findById(RESOURCE_ID)).thenReturn(Optional.of(
                Resource.builder().id(RESOURCE_ID).businessId(BUSINESS_ID).name("Sala 1").build()));
        when(availabilityRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(RESOURCE_ID)).thenReturn(List.of(
                ResourceAvailability.builder().dayOfWeek(5).startTime(LocalTime.of(8, 30)).endTime(LocalTime.of(13, 5)).build()));

        AvailabilityResponse response = service.get(RESOURCE_ID, OWNER);

        assertThat(response.days()).extracting("dayOfWeek").containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(response.days().get(4).ranges()).containsExactly(range("08:30", "13:05"));
        assertThat(response.days().get(0).ranges()).isEmpty();
        verify(businessAccessService).requireOwner(BUSINESS_ID, OWNER);
    }

    @Test
    void getDebeFallarSiElRecursoNoExiste() {
        when(resourceRepository.findById(RESOURCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(RESOURCE_ID, OWNER)).isInstanceOf(ResourceNotFoundException.class);
    }
}
