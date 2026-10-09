package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.common.error.InvalidPaginationException;
import com.codefactory.reservas_backend.provider.application.BusinessDirectoryService;
import com.codefactory.reservas_backend.provider.application.BusinessInfo;
import com.codefactory.reservas_backend.provider.application.BusinessPage;
import com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException;
import com.codefactory.reservas_backend.service.controller.dto.CatalogDtos.BusinessDetailResponse;
import com.codefactory.reservas_backend.service.controller.dto.CatalogDtos.BusinessPageResponse;
import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceImplTest {

    private static final UUID BUSINESS_ID = UUID.randomUUID();

    @Mock
    private BusinessDirectoryService directory;
    @Mock
    private ServiceOfferingRepository serviceRepository;

    private CatalogServiceImpl catalog;

    @BeforeEach
    void setUp() {
        catalog = new CatalogServiceImpl(directory, serviceRepository);
    }

    @Test
    void listBusinessesDebeDevolverLosNegociosPorNombreSinMensaje() {
        when(directory.list(0, 20)).thenReturn(new BusinessPage(
                List.of(new BusinessInfo(BUSINESS_ID, "Barbería Central")), 0, 20, 1, 1));

        BusinessPageResponse response = catalog.listBusinesses(0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).name()).isEqualTo("Barbería Central");
        assertThat(response.message()).isNull();
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void listBusinessesDebeAvisarCuandoElCatalogoEstaVacio() {
        when(directory.list(0, 20)).thenReturn(new BusinessPage(List.of(), 0, 20, 0, 0));

        BusinessPageResponse response = catalog.listBusinesses(0, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.message()).isEqualTo(CatalogServiceImpl.NO_BUSINESSES_MESSAGE);
    }

    @Test
    void listBusinessesNoDebeDarMensajeAlPedirUnaPaginaFueraDeRango() {
        when(directory.list(9, 20)).thenReturn(new BusinessPage(List.of(), 9, 20, 5, 1));

        assertThat(catalog.listBusinesses(9, 20).message()).isNull();
    }

    @Test
    void listBusinessesDebeRecortarElTamanoAlTopeMaximo() {
        when(directory.list(0, CatalogService.MAX_SIZE)).thenReturn(
                new BusinessPage(List.of(), 0, CatalogService.MAX_SIZE, 0, 0));

        BusinessPageResponse response = catalog.listBusinesses(0, 1000);

        assertThat(response.size()).isEqualTo(CatalogService.MAX_SIZE);
        verify(directory).list(0, CatalogService.MAX_SIZE);
    }

    @Test
    void listBusinessesDebeRechazarPaginasNegativasOTamanosInvalidos() {
        assertThatThrownBy(() -> catalog.listBusinesses(-1, 10)).isInstanceOf(InvalidPaginationException.class);
        assertThatThrownBy(() -> catalog.listBusinesses(0, 0)).isInstanceOf(InvalidPaginationException.class);
        assertThatThrownBy(() -> catalog.listBusinesses(0, -5)).isInstanceOf(InvalidPaginationException.class);
        verify(directory, never()).list(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void getBusinessDebeMostrarSoloLosServiciosActivosConSusDatos() {
        when(directory.get(BUSINESS_ID)).thenReturn(new BusinessInfo(BUSINESS_ID, "Barbería Central"));
        when(serviceRepository.findByBusinessIdAndActiveTrueOrderByNameAsc(BUSINESS_ID)).thenReturn(List.of(
                ServiceOffering.builder().id(UUID.randomUUID()).businessId(BUSINESS_ID).name("Corte")
                        .description("Con lavado").durationMinutes(45).priceCop(30_000L).active(true).build()));

        BusinessDetailResponse response = catalog.getBusiness(BUSINESS_ID);

        assertThat(response.name()).isEqualTo("Barbería Central");
        assertThat(response.services()).hasSize(1);
        assertThat(response.services().get(0).durationMinutes()).isEqualTo(45);
        assertThat(response.services().get(0).priceCop()).isEqualTo(30_000L);
        assertThat(response.message()).isNull();
    }

    @Test
    void getBusinessDebeAvisarSiNoTieneServiciosDisponibles() {
        when(directory.get(BUSINESS_ID)).thenReturn(new BusinessInfo(BUSINESS_ID, "Sin servicios"));
        when(serviceRepository.findByBusinessIdAndActiveTrueOrderByNameAsc(BUSINESS_ID)).thenReturn(List.of());

        BusinessDetailResponse response = catalog.getBusiness(BUSINESS_ID);

        assertThat(response.services()).isEmpty();
        assertThat(response.message()).isEqualTo(CatalogServiceImpl.NO_SERVICES_MESSAGE);
    }

    @Test
    void getBusinessDebePropagarElNegocioInexistente() {
        when(directory.get(BUSINESS_ID)).thenThrow(new BusinessNotFoundException("no existe"));

        assertThatThrownBy(() -> catalog.getBusiness(BUSINESS_ID)).isInstanceOf(BusinessNotFoundException.class);
        verify(serviceRepository, never()).findByBusinessIdAndActiveTrueOrderByNameAsc(BUSINESS_ID);
    }
}
