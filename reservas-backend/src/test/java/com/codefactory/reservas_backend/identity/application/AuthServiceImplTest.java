package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.controller.dto.LoginRequest;
import com.codefactory.reservas_backend.identity.controller.dto.LoginResponse;
import com.codefactory.reservas_backend.identity.domain.InvalidCredentialsException;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.SessionRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import com.codefactory.reservas_backend.identity.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de AuthServiceImpl, mapeadas a los escenarios Gherkin de
 * HU 02 - Inicio de sesión.txt y HU 04 - Cerrar sesión.txt.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private MfaService mfaService;
    @Mock
    private AuditService auditService;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, passwordEncoder, jwtTokenProvider, sessionRepository, mfaService, auditService);
    }

    private User buildUser(RoleName roleName, boolean enabled) {
        Role role = Role.builder().id(UUID.randomUUID()).name(roleName).build();
        return User.builder()
                .id(UUID.randomUUID())
                .email("usuario@example.com")
                .passwordHash("HASH_ALMACENADO")
                .enabled(enabled)
                .roles(Set.of(role))
                .build();
    }

    private JwtTokenProvider.IssuedToken issuedToken() {
        return new JwtTokenProvider.IssuedToken("jwt-emitido", "jti-123", Instant.now().plusSeconds(3600));
    }

    @Test
    void debeAutenticarConCredencialesValidasYEmitirToken() {
        User cliente = buildUser(RoleName.CLIENTE, true);
        LoginRequest request = new LoginRequest();
        request.setEmail(cliente.getEmail());
        request.setPassword("Segura#2026");

        when(userRepository.findByEmailIgnoreCase(cliente.getEmail())).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("Segura#2026", "HASH_ALMACENADO")).thenReturn(true);
        when(jwtTokenProvider.issueToken(cliente.getId().toString())).thenReturn(issuedToken());

        LoginResponse response = service.login(request, "127.0.0.1");

        assertThat(response.getRole()).isEqualTo("CLIENTE");
        assertThat(response.getToken()).isEqualTo("jwt-emitido");
        assertThat(response.getExpiresIn()).isCloseTo(3600, org.assertj.core.data.Offset.offset(5L));
        verify(sessionRepository).save(any());
        verify(auditService).registerEvent(eq(AuditEventType.LOGIN), eq(cliente.getEmail()), eq("SUCCESS"), any(), any());
    }

    @Test
    void debeRechazarCredencialesCuandoElCorreoNoExiste() {
        LoginRequest request = new LoginRequest();
        request.setEmail("inexistente@example.com");
        request.setPassword("cualquiera");

        when(userRepository.findByEmailIgnoreCase("inexistente@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(request, "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void debeRechazarCredencialesConContrasenaIncorrecta() {
        User cliente = buildUser(RoleName.CLIENTE, true);
        LoginRequest request = new LoginRequest();
        request.setEmail(cliente.getEmail());
        request.setPassword("incorrecta");

        when(userRepository.findByEmailIgnoreCase(cliente.getEmail())).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("incorrecta", "HASH_ALMACENADO")).thenReturn(false);

        assertThatThrownBy(() -> service.login(request, "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void debeRechazarLoginDeCuentaDeshabilitada() {
        User deshabilitado = buildUser(RoleName.CLIENTE, false);
        LoginRequest request = new LoginRequest();
        request.setEmail(deshabilitado.getEmail());
        request.setPassword("Segura#2026");

        when(userRepository.findByEmailIgnoreCase(deshabilitado.getEmail())).thenReturn(Optional.of(deshabilitado));
        when(passwordEncoder.matches("Segura#2026", "HASH_ALMACENADO")).thenReturn(true);

        assertThatThrownBy(() -> service.login(request, "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void debeExigirCodigoMfaValidoParaUnAdministradorConMfaActiva() {
        User admin = buildUser(RoleName.ADMINISTRADOR, true);
        LoginRequest request = new LoginRequest();
        request.setEmail(admin.getEmail());
        request.setPassword("Segura#2026");
        request.setMfaCode("000000");

        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Segura#2026", "HASH_ALMACENADO")).thenReturn(true);
        when(mfaService.isEnabled(admin.getId())).thenReturn(true);
        when(mfaService.verifyCode(admin.getId(), "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.login(request, "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void debePermitirLoginDeAdministradorConCodigoMfaValido() {
        User admin = buildUser(RoleName.ADMINISTRADOR, true);
        LoginRequest request = new LoginRequest();
        request.setEmail(admin.getEmail());
        request.setPassword("Segura#2026");
        request.setMfaCode("111111");

        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Segura#2026", "HASH_ALMACENADO")).thenReturn(true);
        when(mfaService.isEnabled(admin.getId())).thenReturn(true);
        when(mfaService.verifyCode(admin.getId(), "111111")).thenReturn(true);
        when(jwtTokenProvider.issueToken(admin.getId().toString())).thenReturn(issuedToken());

        LoginResponse response = service.login(request, "127.0.0.1");

        assertThat(response.getRole()).isEqualTo("ADMINISTRADOR");
        verify(sessionRepository).save(any());
    }

    @Test
    void debePermitirLoginDeAdministradorSinMfaActivaTodaviaSinExigirCodigo() {
        // Admin recién ascendido (HU-05): triggerMandatorySetup ya creó una
        // fila Mfa pendiente, pero isEnabled() sigue false hasta que el
        // usuario complete /mfa/activate -> no se bloquea el login.
        User admin = buildUser(RoleName.ADMINISTRADOR, true);
        LoginRequest request = new LoginRequest();
        request.setEmail(admin.getEmail());
        request.setPassword("Segura#2026");

        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Segura#2026", "HASH_ALMACENADO")).thenReturn(true);
        when(mfaService.isEnabled(admin.getId())).thenReturn(false);
        when(jwtTokenProvider.issueToken(admin.getId().toString())).thenReturn(issuedToken());

        LoginResponse response = service.login(request, "127.0.0.1");

        assertThat(response.getRole()).isEqualTo("ADMINISTRADOR");
        verify(mfaService, never()).verifyCode(any(), any());
    }

    @Test
    void logoutDebeRevocarTodasLasSesionesYAuditarElEvento() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(RoleName.CLIENTE, true)));

        service.logout(userId, "127.0.0.1");

        verify(sessionRepository).revokeAllByUserId(userId);
        verify(auditService).registerEvent(eq(AuditEventType.LOGOUT), any(), eq("SUCCESS"), any(), any());
    }
}
