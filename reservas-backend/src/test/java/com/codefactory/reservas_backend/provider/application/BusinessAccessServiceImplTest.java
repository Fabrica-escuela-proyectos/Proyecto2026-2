package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.domain.Business;
import com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException;
import com.codefactory.reservas_backend.provider.domain.Provider;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import com.codefactory.reservas_backend.provider.infrastructure.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessAccessServiceImplTest {

    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final UUID PROVIDER_ID = UUID.randomUUID();
    private static final UUID OWNER_USER_ID = UUID.randomUUID();

    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private ProviderRepository providerRepository;

    private BusinessAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BusinessAccessServiceImpl(businessRepository, providerRepository);
    }

    @Test
    void requireOwnerDebePermitirAlDuenioDelNegocio() {
        stubBusinessOfProvider();
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider(OWNER_USER_ID)));

        assertThatCode(() -> service.requireOwner(BUSINESS_ID, identity(OWNER_USER_ID, "PROVEEDOR")))
                .doesNotThrowAnyException();
    }

    @Test
    void requireOwnerDebeDenegarAOtroProveedor() {
        stubBusinessOfProvider();
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider(OWNER_USER_ID)));

        assertThatThrownBy(() -> service.requireOwner(BUSINESS_ID, identity(UUID.randomUUID(), "PROVEEDOR")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireOwnerDebeDenegarTambienAUnAdministrador() {
        stubBusinessOfProvider();
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider(OWNER_USER_ID)));

        assertThatThrownBy(() -> service.requireOwner(BUSINESS_ID, identity(UUID.randomUUID(), "ADMINISTRADOR")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireOwnerDebeDenegarSiElProveedorDelNegocioYaNoExiste() {
        stubBusinessOfProvider();
        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOwner(BUSINESS_ID, identity(OWNER_USER_ID, "PROVEEDOR")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireOwnerDebeFallarSiElNegocioNoExiste() {
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOwner(BUSINESS_ID, identity(OWNER_USER_ID, "PROVEEDOR")))
                .isInstanceOf(BusinessNotFoundException.class);
    }

    private void stubBusinessOfProvider() {
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(
                Business.builder().id(BUSINESS_ID).providerId(PROVIDER_ID).name("Negocio").build()));
    }

    private static Provider provider(UUID userId) {
        return Provider.builder().id(PROVIDER_ID).userId(userId).build();
    }

    private static UserIdentity identity(UUID id, String role) {
        return new UserIdentity(id, "usuario@example.com", role);
    }
}
