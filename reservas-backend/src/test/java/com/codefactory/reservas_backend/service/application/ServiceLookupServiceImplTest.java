package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.domain.ServiceResource;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import com.codefactory.reservas_backend.service.infrastructure.ServiceResourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceLookupServiceImplTest {

    @Mock
    private ServiceOfferingRepository serviceRepository;
    @Mock
    private ServiceResourceRepository assignmentRepository;
    @InjectMocks
    private ServiceLookupServiceImpl lookup;

    @Test
    void findByIdDebeExponerSoloLosDatosPublicosDelServicio() {
        UUID id = UUID.randomUUID();
        UUID business = UUID.randomUUID();
        when(serviceRepository.findById(id)).thenReturn(Optional.of(ServiceOffering.builder()
                .id(id).businessId(business).name("Corte").durationMinutes(45).priceCop(30_000L).active(true).build()));

        assertThat(lookup.findById(id)).contains(new ServiceInfo(id, business, "Corte", 45, 30_000L, true));
    }

    @Test
    void findByIdDevuelveVacioSiNoExiste() {
        UUID id = UUID.randomUUID();
        when(serviceRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(lookup.findById(id)).isEmpty();
    }

    @Test
    void assignedResourceIdsDebeDevolverLosIdsAsignados() {
        UUID service = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        when(assignmentRepository.findByIdServiceId(service))
                .thenReturn(List.of(new ServiceResource(service, r1), new ServiceResource(service, r2)));

        assertThat(lookup.assignedResourceIds(service)).containsExactlyInAnyOrder(r1, r2);
    }
}
