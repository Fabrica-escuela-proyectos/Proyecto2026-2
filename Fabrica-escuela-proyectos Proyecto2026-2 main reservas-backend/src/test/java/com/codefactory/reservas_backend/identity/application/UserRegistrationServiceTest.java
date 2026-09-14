package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.identity.controller.dto.RegisterUserRequest;
import com.codefactory.reservas_backend.identity.domain.DuplicateEmailException;
import com.codefactory.reservas_backend.identity.domain.DuplicatePhoneException;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.RegistrationRateLimiter;
import com.codefactory.reservas_backend.identity.infrastructure.RoleRepository;
import com.codefactory.reservas_backend.identity.infrastructure.TooManyRequestsException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de UserRegistrationService, mapeadas 1:1 a los
 * escenarios Gherkin de HU-01-Registrar-cliente.txt.
 * Ver tabla de mapeo completa en docs/HU-01-checklist.md.
 */
@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;
    @Mock
    private RegistrationRateLimiter rateLimiter;

    private UserRegistrationService service;
    private RegisterUserRequest request;

    @BeforeEach
    void setUp() {
        service = new UserRegistrationService(userRepository, roleRepository, passwordEncoder, auditService, rateLimiter);

        request = new RegisterUserRequest();
        request.setFullName("Simon Betancur Sosa");
        request.setEmail("simon@example.com");
        request.setCellphone("3001234567");
        request.setPassword("Segura#2026");
    }

    @Test
    void debeCrearUsuarioConDatosValidosYRolCliente() {
        // Arrange
        Role clienteRole = Role.builder().id(UUID.randomUUID()).name(RoleName.CLIENTE).build();
        when(rateLimiter.isBlocked(any())).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false);
        when(userRepository.existsByCellphone(request.getCellphone())).thenReturn(false);
        when(roleRepository.findByName(RoleName.CLIENTE)).thenReturn(Optional.of(clienteRole));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("HASH_SEGURO");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        // Act
        var response = service.register(request, "127.0.0.1");

        // Assert
        assertThat(response.getEmail()).isEqualTo(request.getEmail());
        assertThat(response.getRole()).isEqualTo("CLIENTE");
        verify(passwordEncoder).encode(request.getPassword());
        verify(auditService).registerEvent(any(), eq(request.getEmail()), eq("SUCCESS"), isNull(), any());
    }

    @Test
    void debeRechazarRegistroConCorreoDuplicado() {
        // Arrange
        when(rateLimiter.isBlocked(any())).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(true);

        // Act / Assert
        assertThatThrownBy(() -> service.register(request, "127.0.0.1"))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void debeRechazarRegistroConCelularDuplicado() {
        // Arrange
        when(rateLimiter.isBlocked(any())).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false);
        when(userRepository.existsByCellphone(request.getCellphone())).thenReturn(true);

        // Act / Assert
        assertThatThrownBy(() -> service.register(request, "127.0.0.1"))
                .isInstanceOf(DuplicatePhoneException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void debeBloquearTemporalmenteTrasMultiplesIntentos() {
        // Arrange
        when(rateLimiter.isBlocked(any())).thenReturn(true);

        // Act / Assert
        assertThatThrownBy(() -> service.register(request, "127.0.0.1"))
                .isInstanceOf(TooManyRequestsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void debeAlmacenarContrasenaComoHashNoReversible() {
        // Arrange
        Role clienteRole = Role.builder().id(UUID.randomUUID()).name(RoleName.CLIENTE).build();
        when(rateLimiter.isBlocked(any())).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(userRepository.existsByCellphone(any())).thenReturn(false);
        when(roleRepository.findByName(RoleName.CLIENTE)).thenReturn(Optional.of(clienteRole));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("$2a$10$hashSimulado");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.register(request, "127.0.0.1");

        // Assert: el repositorio nunca recibe la contraseña en texto plano,
        // solo el hash devuelto por el PasswordEncoder.
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(u ->
                u.getPasswordHash().equals("$2a$10$hashSimulado")
                        && !u.getPasswordHash().equals(request.getPassword())));
    }

    @Test
    void debeFallarSiElRolClienteNoEstaInicializado() {
        // Arrange: refleja un problema de datos semilla (seed de roles
        // faltante), no un caso de HU-01 en sí, pero es una salvaguarda
        // útil para detectar un entorno mal configurado.
        when(rateLimiter.isBlocked(any())).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(userRepository.existsByCellphone(any())).thenReturn(false);
        when(roleRepository.findByName(RoleName.CLIENTE)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.register(request, "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class);
    }
}
