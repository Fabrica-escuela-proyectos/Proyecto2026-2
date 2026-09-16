package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.identity.domain.DuplicateEmailException;
import com.codefactory.reservas_backend.identity.domain.DuplicatePhoneException;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.RoleRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * UserProvisioningService es el contrato que identity expone a otros
 * módulos (hoy: provider, HU-03) para crear cuentas con un rol distinto de
 * CLIENTE, sin darles acceso directo a UserRepository/RoleRepository
 * (ADR-003). Reutiliza las mismas reglas de HU-01 (unicidad de
 * correo/celular, hash de contraseña) para cualquier rol.
 */
@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserProvisioningServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserProvisioningServiceImpl(userRepository, roleRepository, passwordEncoder);
    }

    @Test
    void debeAprovisionarUnUsuarioConElRolSolicitado() {
        Role proveedorRole = Role.builder().id(UUID.randomUUID()).name(RoleName.PROVEEDOR).build();
        when(userRepository.existsByEmailIgnoreCase("carlos@example.com")).thenReturn(false);
        when(userRepository.existsByCellphone("3019876543")).thenReturn(false);
        when(roleRepository.findByName(RoleName.PROVEEDOR)).thenReturn(Optional.of(proveedorRole));
        when(passwordEncoder.encode("Segura#2026")).thenReturn("HASH_SEGURO");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        UserProvisioningService.ProvisionedUser result = service.provisionUser(
                "Carlos Gómez", "carlos@example.com", "3019876543", "Segura#2026", RoleName.PROVEEDOR);

        assertThat(result.role()).isEqualTo(RoleName.PROVEEDOR);
        assertThat(result.email()).isEqualTo("carlos@example.com");
    }

    @Test
    void debeRechazarCorreoDuplicado() {
        when(userRepository.existsByEmailIgnoreCase("carlos@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.provisionUser(
                "Carlos Gómez", "carlos@example.com", "3019876543", "Segura#2026", RoleName.PROVEEDOR))
                .isInstanceOf(DuplicateEmailException.class);
        verify_neverSaved();
    }

    @Test
    void debeRechazarCelularDuplicado() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(userRepository.existsByCellphone("3019876543")).thenReturn(true);

        assertThatThrownBy(() -> service.provisionUser(
                "Carlos Gómez", "carlos@example.com", "3019876543", "Segura#2026", RoleName.PROVEEDOR))
                .isInstanceOf(DuplicatePhoneException.class);
        verify_neverSaved();
    }

    @Test
    void debeFallarSiElRolSolicitadoNoEstaInicializado() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(userRepository.existsByCellphone(any())).thenReturn(false);
        when(roleRepository.findByName(RoleName.PROVEEDOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.provisionUser(
                "Carlos Gómez", "carlos@example.com", "3019876543", "Segura#2026", RoleName.PROVEEDOR))
                .isInstanceOf(IllegalStateException.class);
    }

    private void verify_neverSaved() {
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(any());
    }
}
