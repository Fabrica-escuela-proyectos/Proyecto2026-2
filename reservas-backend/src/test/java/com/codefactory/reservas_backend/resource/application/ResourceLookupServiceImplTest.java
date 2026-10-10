package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.domain.ResourceType;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceLookupServiceImplTest {

    private static final UUID BUSINESS_ID = UUID.randomUUID();

    @Mock
    private ResourceRepository repository;

    private ResourceLookupServiceImpl lookup;

    @BeforeEach
    void setUp() {
        lookup = new ResourceLookupServiceImpl(repository);
    }

    private static Resource resource(String name) {
        return Resource.builder().id(UUID.randomUUID()).businessId(BUSINESS_ID).name(name)
                .type(ResourceType.EQUIPO).active(true).build();
    }

    @Test
    void findInBusinessDebeConsultarSoloLosRecursosDelNegocio() {
        Resource r = resource("Camilla");
        Set<UUID> ids = Set.of(r.getId());
        when(repository.findByBusinessIdAndIdIn(BUSINESS_ID, ids)).thenReturn(List.of(r));

        List<ResourceInfo> result = lookup.findInBusiness(BUSINESS_ID, ids);

        assertThat(result).containsExactly(new ResourceInfo(r.getId(), BUSINESS_ID, "Camilla", "EQUIPO", true));
    }

    @Test
    void findInBusinessConListaVaciaNoConsultaLaBase() {
        assertThat(lookup.findInBusiness(BUSINESS_ID, Set.of())).isEmpty();
        verify(repository, never()).findByBusinessIdAndIdIn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void findByIdsDebeOrdenarPorNombreSinMayusculas() {
        Resource b = resource("beta");
        Resource a = resource("Alfa");
        Set<UUID> ids = Set.of(a.getId(), b.getId());
        when(repository.findAllById(ids)).thenReturn(List.of(b, a));

        assertThat(lookup.findByIds(ids)).extracting(ResourceInfo::name).containsExactly("Alfa", "beta");
    }

    @Test
    void lockActiveDebeBloquearLaFilaYDecirSiEstaActivo() {
        UUID active = UUID.randomUUID();
        UUID inactive = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(repository.lockAndReadActive(active)).thenReturn(java.util.Optional.of(true));
        when(repository.lockAndReadActive(inactive)).thenReturn(java.util.Optional.of(false));
        when(repository.lockAndReadActive(missing)).thenReturn(java.util.Optional.empty());

        assertThat(lookup.lockActive(active)).isTrue();
        assertThat(lookup.lockActive(inactive)).isFalse();
        assertThat(lookup.lockActive(missing)).isFalse();
    }

    @Test
    void findByIdsConListaVaciaNoConsultaLaBase() {
        assertThat(lookup.findByIds(List.of())).isEmpty();
        verify(repository, never()).findAllById(org.mockito.ArgumentMatchers.any());
    }
}
