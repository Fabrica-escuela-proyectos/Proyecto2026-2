package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Pruebas de la implementación mínima de AuthorizationService descrita en
 * interfaces-modulos-sprint-1.md sección 8. hasPermission se prueba
 * verificando que falla explícitamente (ver Javadoc de
 * AuthorizationServiceImpl) en vez de simular un catálogo de permisos que
 * todavía no existe.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private AuthorizationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationServiceImpl(userRepository);
    }

    @Test
    void debeConfirmarRolCuandoElUsuarioLoTiene() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Role clienteRole = Role.builder().id(UUID.randomUUID()).name(RoleName.CLIENTE).build();
        User user = User.builder().id(userId).roles(Set.of(clienteRole)).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        boolean resultado = service.hasRole(userId, "CLIENTE");

        // Assert
        assertThat(resultado).isTrue();
    }

    @Test
    void debeNegarRolCuandoElUsuarioNoLoTiene() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Role clienteRole = Role.builder().id(UUID.randomUUID()).name(RoleName.CLIENTE).build();
        User user = User.builder().id(userId).roles(Set.of(clienteRole)).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        boolean resultado = service.hasRole(userId, "ADMINISTRADOR");

        // Assert
        assertThat(resultado).isFalse();
    }

    @Test
    void debeNegarRolCuandoElUsuarioNoExiste() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act
        boolean resultado = service.hasRole(userId, "CLIENTE");

        // Assert
        assertThat(resultado).isFalse();
    }

    @Test
    void hasPermissionDebeFallarExplicitamentePorqueNoHayCatalogoDePermisosAun() {
        assertThatThrownBy(() -> service.hasPermission(UUID.randomUUID(), "cualquier-permiso"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
