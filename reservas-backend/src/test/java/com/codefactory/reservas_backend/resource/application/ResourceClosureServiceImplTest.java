package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.common.config.TimeConfig;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.application.BusinessAccessService;
import com.codefactory.reservas_backend.resource.controller.dto.UpdateResourceRequest;
import com.codefactory.reservas_backend.resource.domain.ClosureConflictException;
import com.codefactory.reservas_backend.resource.domain.ClosureNotFoundException;
import com.codefactory.reservas_backend.resource.domain.DuplicateResourceNameException;
import com.codefactory.reservas_backend.resource.domain.InvalidClosureException;
import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.domain.ResourceClosure;
import com.codefactory.reservas_backend.resource.domain.ResourceType;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceClosureRepository;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU-21 (ResourceClosureServiceImpl) y HU-15 (ResourceServiceImpl.update). */
@ExtendWith(MockitoExtension.class)
class ResourceClosureServiceImplTest {

    /** Lunes 16-nov-2026, 10:00 en Bogotá. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 10, 0);
    private static final UserIdentity PROVIDER = new UserIdentity(UUID.randomUUID(), "p@example.com", "PROVEEDOR");

    @Mock
    private ResourceClosureRepository closureRepository;
    @Mock
    private ResourceRepository resourceRepository;
    @Mock
    private BusinessAccessService businessAccessService;
    @Mock
    private AuditService auditService;

    private ResourceClosureServiceImpl service;
    private ResourceServiceImpl resources;
    private Resource resource;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(TimeConfig.BUSINESS_ZONE).toInstant(), TimeConfig.BUSINESS_ZONE);
        service = new ResourceClosureServiceImpl(closureRepository, resourceRepository, businessAccessService, auditService, clock);
        resources = new ResourceServiceImpl(resourceRepository, businessAccessService, auditService);
        resource = Resource.builder().id(UUID.randomUUID()).businessId(UUID.randomUUID()).name("Sala 1")
                .type(ResourceType.SALA).active(true).build();
    }

    private static Instant bogota(String text) {
        return LocalDateTime.parse(text).atZone(TimeConfig.BUSINESS_ZONE).toInstant();
    }

    // --- resolvePeriod ---

    @Test
    void unDiaCompletoVaDeMedianocheAMedianoche() {
        ClosurePeriod p = service.resolvePeriod("2026-11-20", null, null);

        assertThat(p.start()).isEqualTo(bogota("2026-11-20T00:00"));
        assertThat(p.end()).isEqualTo(bogota("2026-11-21T00:00"));
    }

    @Test
    void unRangoAceptaEspacioOTComoSeparador() {
        ClosurePeriod p = service.resolvePeriod(null, "2026-11-20 14:00", "2026-11-20T18:30");

        assertThat(p.start()).isEqualTo(bogota("2026-11-20T14:00"));
        assertThat(p.end()).isEqualTo(bogota("2026-11-20T18:30"));
    }

    @Test
    void hoyMismoSePuedeCerrarAunqueElDiaYaEmpezo() {
        assertThat(service.resolvePeriod("2026-11-16", null, null).start()).isEqualTo(bogota("2026-11-16T00:00"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "2026-11-20 14:00|2026-11-20 10:00|posterior",
            "2026-11-20 09:00|2026-11-20 09:00|posterior",
            "2026-09-01 00:00|2026-09-01 23:59|igual o posterior",
            "2026-11-16 08:00|2026-11-16 09:30|igual o posterior",
            "2026-11-20 08:00|2028-01-01 08:00|366 días",
            "2026/11/20 08:00|2026-11-20 09:00|formato"}, delimiter = '|')
    void periodosInvalidos(String start, String end, String message) {
        assertThatThrownBy(() -> service.resolvePeriod(null, start, end))
                .isInstanceOf(InvalidClosureException.class).hasMessageContaining(message);
    }

    @Test
    void fechaYRangoALaVezOIncompletosSonInvalidos() {
        assertThatThrownBy(() -> service.resolvePeriod("2026-11-20", "2026-11-20 10:00", "2026-11-20 11:00"))
                .isInstanceOf(InvalidClosureException.class);
        assertThatThrownBy(() -> service.resolvePeriod(null, "2026-11-20 10:00", null)).isInstanceOf(InvalidClosureException.class);
        assertThatThrownBy(() -> service.resolvePeriod(" ", null, null)).isInstanceOf(InvalidClosureException.class);
        assertThatThrownBy(() -> service.resolvePeriod("20-11-2026", null, null)).isInstanceOf(InvalidClosureException.class);
    }

    // --- crear, traslapes, listar y eliminar ---

    @Test
    void unPeriodoTraslapadoDa409() {
        ClosurePeriod p = new ClosurePeriod(bogota("2026-11-20T10:00"), bogota("2026-11-20T12:00"));
        when(closureRepository.existsOverlapping(resource.getId(), p.start(), p.end())).thenReturn(true);

        assertThatThrownBy(() -> service.requireNoOverlap(resource.getId(), p)).isInstanceOf(ClosureConflictException.class);
    }

    @Test
    void crearGuardaElCierreConMotivoYAuditaLasReservasCanceladas() {
        ClosurePeriod p = new ClosurePeriod(bogota("2026-11-20T00:00"), bogota("2026-11-21T00:00"));
        when(closureRepository.saveAndFlush(any(ResourceClosure.class))).thenAnswer(inv -> {
            ResourceClosure c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        ClosureInfo info = service.create(resource.getId(), p, " Festivo ", 2, PROVIDER, "ip");

        assertThat(info.fullDay()).isTrue();
        assertThat(info.reason()).isEqualTo("Festivo");
        assertThat(info.start()).isEqualTo("2026-11-20T00:00");
        verify(auditService).registerEvent(eq(AuditEventType.CIERRE_RECURSO), eq("p@example.com"), eq("SUCCESS"),
                contains("2 reserva(s) cancelada(s)"), eq("ip"));
    }

    @Test
    void eliminarUnCierreFuturoLoBorraYUnoQueYaEmpezoDa409() {
        when(resourceRepository.findByIdForUpdate(resource.getId())).thenReturn(Optional.of(resource));
        ResourceClosure future = ResourceClosure.builder().id(UUID.randomUUID()).resourceId(resource.getId())
                .startAt(bogota("2026-11-16T12:00")).endAt(bogota("2026-11-16T13:00")).build();
        ResourceClosure started = ResourceClosure.builder().id(UUID.randomUUID()).resourceId(resource.getId())
                .startAt(bogota("2026-11-16T09:00")).endAt(bogota("2026-11-16T11:00")).build();
        when(closureRepository.findById(future.getId())).thenReturn(Optional.of(future));
        when(closureRepository.findById(started.getId())).thenReturn(Optional.of(started));

        service.delete(resource.getId(), future.getId(), PROVIDER, "ip");

        verify(closureRepository).delete(future);
        assertThatThrownBy(() -> service.delete(resource.getId(), started.getId(), PROVIDER, "ip"))
                .isInstanceOf(ClosureConflictException.class).hasMessageContaining("ya comenzó");
        verify(closureRepository, never()).delete(started);
    }

    @Test
    void unCierreDeOtroRecursoNoSeEncuentra() {
        when(resourceRepository.findByIdForUpdate(resource.getId())).thenReturn(Optional.of(resource));
        ResourceClosure foreign = ResourceClosure.builder().id(UUID.randomUUID()).resourceId(UUID.randomUUID())
                .startAt(bogota("2026-11-20T09:00")).endAt(bogota("2026-11-20T11:00")).build();
        when(closureRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.delete(resource.getId(), foreign.getId(), PROVIDER, "ip"))
                .isInstanceOf(ClosureNotFoundException.class);
    }

    @Test
    void listarExigePertenenciaYDevuelveLosVigentes() {
        when(resourceRepository.findById(resource.getId())).thenReturn(Optional.of(resource));
        when(closureRepository.findByResourceIdAndEndAtAfterOrderByStartAtAsc(eq(resource.getId()), any())).thenReturn(java.util.List.of(
                ResourceClosure.builder().id(UUID.randomUUID()).resourceId(resource.getId())
                        .startAt(bogota("2026-11-20T09:00")).endAt(bogota("2026-11-20T11:00")).build()));

        assertThat(service.listUpcoming(resource.getId(), PROVIDER)).extracting(ClosureInfo::end).containsExactly("2026-11-20T11:00");
        verify(businessAccessService).requireOwner(resource.getBusinessId(), PROVIDER);
    }

    // --- HU-15: editar recurso ---

    @Test
    void editarRecursoCambiaNombreYTipoYAudita() {
        when(resourceRepository.findByIdForUpdate(resource.getId())).thenReturn(Optional.of(resource));
        UpdateResourceRequest r = new UpdateResourceRequest();
        r.setName(" Sala Azul ");
        r.setType("equipo");

        var response = resources.update(resource.getId(), r, PROVIDER, "ip");

        assertThat(response.getName()).isEqualTo("Sala Azul");
        assertThat(response.getType()).isEqualTo("EQUIPO");
        verify(auditService).registerEvent(eq(AuditEventType.EDICION_RECURSO), anyString(), eq("SUCCESS"),
                contains("nombre, tipo"), eq("ip"));
    }

    @Test
    void editarRecursoConNombreDeOtroDa409YSinCambiosNoGuarda() {
        when(resourceRepository.findByIdForUpdate(resource.getId())).thenReturn(Optional.of(resource));
        when(resourceRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(resource.getBusinessId(), "Sala 2", resource.getId()))
                .thenReturn(true);
        UpdateResourceRequest dup = new UpdateResourceRequest();
        dup.setName("Sala 2");
        UpdateResourceRequest same = new UpdateResourceRequest();
        same.setName("Sala 1");
        same.setType("SALA");

        assertThatThrownBy(() -> resources.update(resource.getId(), dup, PROVIDER, "ip"))
                .isInstanceOf(DuplicateResourceNameException.class);
        resources.update(resource.getId(), same, PROVIDER, "ip");
        verify(resourceRepository, never()).saveAndFlush(any());
    }

    @Test
    void unRangoDeUnDiaCompletoNoSeMarcaComoDiaCompletoSiNoEmpiezaAMedianoche() {
        when(closureRepository.saveAndFlush(any(ResourceClosure.class))).thenAnswer(inv -> inv.getArgument(0));
        ClosurePeriod p = new ClosurePeriod(bogota("2026-11-20T08:00"), bogota("2026-11-20T08:00").plus(Duration.ofDays(1)));

        assertThat(service.create(resource.getId(), p, null, 0, PROVIDER, "ip").fullDay()).isFalse();
    }
}
