package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.provider.controller.dto.ProviderResponse;
import com.codefactory.reservas_backend.provider.domain.Business;
import com.codefactory.reservas_backend.provider.domain.Provider;
import com.codefactory.reservas_backend.provider.domain.ProviderNotFoundException;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import com.codefactory.reservas_backend.provider.infrastructure.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderQueryServiceTest {

    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private BusinessRepository businessRepository;

    private ProviderQueryService service;

    private static final UUID PROVIDER_ID = UUID.randomUUID();
    private static final UUID OWNER_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProviderQueryService(providerRepository, businessRepository);
    }

    // ---------- getOwnProvider ----------

    @Test
    void getOwnProviderDebeDevolverElProveedorAsociadoAlUsuario() {
        Provider provider = provider();
        when(providerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.of(provider));
        when(businessRepository.findByProviderId(PROVIDER_ID)).thenReturn(List.of());

        ProviderResponse response = service.getOwnProvider(OWNER_USER_ID);

        assertThat(response.getProviderId()).isEqualTo(PROVIDER_ID);
        assertThat(response.getUserId()).isEqualTo(OWNER_USER_ID);
    }

    @Test
    void getOwnProviderDebeFallarSiElUsuarioNoTieneUnNegocioAsociado() {
        when(providerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwnProvider(OWNER_USER_ID))
                .isInstanceOf(ProviderNotFoundException.class);
    }

    // ---------- getProvider: existencia ----------

    @Test
    void getProviderDebeFallarSiElProveedorSolicitadoNoExiste() {
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.empty());
        UserIdentity admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", RoleName.ADMINISTRADOR.name());

        assertThatThrownBy(() -> service.getProvider(PROVIDER_ID, admin))
                .isInstanceOf(ProviderNotFoundException.class);
    }

    // ---------- getProvider: control de acceso (HU-06) ----------

    @Test
    void getProviderDebePermitirQueUnAdministradorConsulteCualquierProveedor() {
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider()));
        when(businessRepository.findByProviderId(PROVIDER_ID)).thenReturn(List.of());

        UserIdentity admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", RoleName.ADMINISTRADOR.name());

        ProviderResponse response = service.getProvider(PROVIDER_ID, admin);

        assertThat(response.getProviderId()).isEqualTo(PROVIDER_ID);
    }

    @Test
    void getProviderDebePermitirQueElDuenoConsulteSuPropioProveedor() {
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider()));
        when(businessRepository.findByProviderId(PROVIDER_ID)).thenReturn(List.of());

        UserIdentity propietario = new UserIdentity(OWNER_USER_ID, "proveedor@example.com", RoleName.PROVEEDOR.name());

        ProviderResponse response = service.getProvider(PROVIDER_ID, propietario);

        assertThat(response.getProviderId()).isEqualTo(PROVIDER_ID);
    }

    @Test
    void getProviderDebeDenegarElAccesoAUnProveedorQueNoEsElDueno() {
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider()));

        // Otro proveedor, con rol PROVEEDOR válido pero sin ser el dueño de este negocio.
        UserIdentity otroProveedor = new UserIdentity(UUID.randomUUID(), "otro@example.com", RoleName.PROVEEDOR.name());

        assertThatThrownBy(() -> service.getProvider(PROVIDER_ID, otroProveedor))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getProviderDebeDenegarElAccesoAUnClienteSinRelacionConElProveedor() {
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider()));

        UserIdentity cliente = new UserIdentity(UUID.randomUUID(), "cliente@example.com", RoleName.CLIENTE.name());

        assertThatThrownBy(() -> service.getProvider(PROVIDER_ID, cliente))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------- toResponse: mapeo de negocios ----------

    @Test
    void getProviderDebeIncluirElResumenDeCadaNegocioAsociado() {
        Business negocio1 = Business.builder().id(UUID.randomUUID()).name("Peluquería Ana").build();
        Business negocio2 = Business.builder().id(UUID.randomUUID()).name("Spa Ana").build();

        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider()));
        when(businessRepository.findByProviderId(PROVIDER_ID)).thenReturn(List.of(negocio1, negocio2));

        UserIdentity admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", RoleName.ADMINISTRADOR.name());

        ProviderResponse response = service.getProvider(PROVIDER_ID, admin);

        assertThat(response.getBusinesses())
                .extracting(ProviderResponse.BusinessSummary::id, ProviderResponse.BusinessSummary::name)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(negocio1.getId(), negocio1.getName()),
                        org.assertj.core.groups.Tuple.tuple(negocio2.getId(), negocio2.getName())
                );
    }

    private Provider provider() {
        return Provider.builder()
                .id(PROVIDER_ID)
                .userId(OWNER_USER_ID)
                .createdAt(Instant.now())
                .build();
    }
}