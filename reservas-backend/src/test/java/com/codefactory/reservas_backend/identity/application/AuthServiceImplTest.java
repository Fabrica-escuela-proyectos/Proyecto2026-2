package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.controller.dto.LoginRequest;
import com.codefactory.reservas_backend.identity.controller.dto.LoginResponse;
import com.codefactory.reservas_backend.identity.domain.InvalidCredentialsException;
import com.codefactory.reservas_backend.identity.domain.MfaRequiredException;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.AuthAttemptLimiter;
import com.codefactory.reservas_backend.identity.infrastructure.SessionRepository;
import com.codefactory.reservas_backend.identity.infrastructure.TooManyRequestsException;
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
 * HU 02 - Inicio de sesión.txt y HU 04 - Cerrar sesión.txt, más la política
 * de MFA de ADR-004 (login en dos pasos, TC-MFA-11..15) y el control de
 * fuerza bruta (OWASP A07).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String IP = "127.0.0.1";
    private static final String LOGIN_KEY = AuthAttemptLimiter.loginKey(IP, "usuario@example.com");

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
    @Mock
    private AuthAttemptLimiter attemptLimiter;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, passwordEncoder, jwtTokenProvider, sessionRepository,
                mfaService, auditService, attemptLimiter);
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

    private LoginRequest loginRequest(String email, String password, String mfaCode) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setMfaCode(mfaCode);
        return request;
    }

    private JwtTokenProvider.IssuedToken issuedToken() {
        return new JwtTokenProvider.IssuedToken("jwt-emitido", "jti-123", Instant.now().plusSeconds(3600));
    }

    // --- HU-02: credenciales ---

    @Test
    void debeAutenticarConCredencialesValidasYEmitirToken() {
        User cliente = buildUser(RoleName.CLIENTE, true);
        LoginRequest request = loginRequest(cliente.getEmail(), "Segura#2026", null);

        when(userRepository.findByEmailIgnoreCase(cliente.getEmail())).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("Segura#2026", "HASH_ALMACENADO")).thenReturn(true);
        when(jwtTokenProvider.issueToken(cliente.getId().toString())).thenReturn(issuedToken());

        LoginResponse response = service.login(request, IP);

        assertThat(response.getRole()).isEqualTo("CLIENTE");
        assertThat(response.getToken()).isEqualTo("jwt-emitido");
        assertThat(response.getExpiresIn()).isCloseTo(3600, org.assertj.core.data.Offset.offset(5L));
        verify(sessionRepository).save(any());
        verify(auditService).registerEvent(eq(AuditEventType.LOGIN), eq(cliente.getEmail()), eq("SUCCESS"), any(), any());
        verify(attemptLimiter).reset(LOGIN_KEY);
    }

    @Test
    void debeRechazarCredencialesCuandoElCorreoNoExiste() {
        LoginRequest request = loginRequest("inexistente@example.com", "cualquiera", null);

        when(userRepository.findByEmailIgnoreCase("inexistente@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(request, IP))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(sessionRepository, never()).save(any());
        verify(attemptLimiter).recordFailure(AuthAttemptLimiter.loginKey(IP, "inexistente@example.com"));
    }

    @Test
    void debeRechazarCredencialesConContrasenaIncorrecta() {
        User cliente = buildUser(RoleName.CLIENTE, true);
        LoginRequest request = loginRequest(cliente.getEmail(), "incorrecta", null);

        when(userRepository.findByEmailIgnoreCase(cliente.getEmail())).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("incorrecta", "HASH_ALMACENADO")).thenReturn(false);

        assertThatThrownBy(() -> service.login(request, IP))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(sessionRepository, never()).save(any());
        verify(attemptLimiter).recordFailure(LOGIN_KEY);
        verify(auditService).registerEvent(eq(AuditEventType.LOGIN), eq(cliente.getEmail()), eq("REJECTED"), any(), any());
    }

    @Test
    void debeRechazarLoginDeCuentaDeshabilitada() {
        User deshabilitado = buildUser(RoleName.CLIENTE, false);
        LoginRequest request = loginRequest(deshabilitado.getEmail(), "Segura#2026", null);

        when(userRepository.findByEmailIgnoreCase(deshabilitado.getEmail())).thenReturn(Optional.of(deshabilitado));
        when(passwordEncoder.matches("Segura#2026", "HASH_ALMACENADO")).thenReturn(true);

        assertThatThrownBy(() -> service.login(request, IP))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // --- Control de fuerza bruta (OWASP A07, ADR-004 P7) ---

    @Test
    void debeResponder429SinComprobarLaContrasenaCuandoLaClaveEstaBloqueada() {
        LoginRequest request = loginRequest("usuario@example.com", "Segura#2026", null);
        when(attemptLimiter.isBlocked(LOGIN_KEY)).thenReturn(true);

        assertThatThrownBy(() -> service.login(request, IP))
                .isInstanceOf(TooManyRequestsException.class);

        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void elBloqueoSeAplicaPorCorreoEnMinusculasYNoEnumeraCuentas() {
        // Misma respuesta (429) exista o no la cuenta: la clave solo depende
        // de lo que escribió el cliente, normalizado a minúsculas.
        LoginRequest request = loginRequest("  USUARIO@Example.com ", "x", null);
        when(attemptLimiter.isBlocked(LOGIN_KEY)).thenReturn(true);

        assertThatThrownBy(() -> service.login(request, IP))
                .isInstanceOf(TooManyRequestsException.class);
    }

    // --- MFA: login en dos pasos (ADR-004 P5; TC-MFA-11..15) ---

    @Test
    void debePermitirLoginDeAdministradorConCodigoMfaValido() {
        // TC-MFA-11
        User admin = buildUser(RoleName.ADMINISTRADOR, true);
        LoginRequest request = loginRequest(admin.getEmail(), "Segura#2026", "111111");

        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Segura#2026", "HASH_ALMACENADO")).thenReturn(true);
        when(mfaService.isEnabled(admin.getId())).thenReturn(true);
        when(mfaService.verifyCode(admin.getId(), "111111")).thenReturn(true);
        when(jwtTokenProvider.issueToken(admin.getId().toString())).thenReturn(issuedToken());

        LoginResponse response = service.login(request, IP);

        assertThat(response.getRole()).isEqualTo("ADMINISTRADOR");
        verify(sessionRepository).save(any());
        verify(attemptLimiter).reset(LOGIN_KEY);
    }

    @Test
    void debePedirElCodigoMfaCuandoLaContrasenaEsValidaPeroNoLlegaCodigo() {
        // TC-MFA-12: 401 MFA_REQUIRED, sin token, y NO cuenta como fallo.
        User admin = buildUser(RoleName.ADMINISTRADOR, true);
        LoginRequest request = loginRequest(admin.getEmail(), "Segura#2026", null);

        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Segura#2026", "HASH_ALMACENADO")).thenReturn(true);
        when(mfaService.isEnabled(admin.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.login(request, IP))
                .isInstanceOf(MfaRequiredException.class);

        verify(sessionRepository, never()).save(any());
        verify(jwtTokenProvider, never()).issueToken(any());
        verify(attemptLimiter, never()).recordFailure(any());
        verify(auditService).registerEvent(eq(AuditEventType.LOGIN), eq(admin.getEmail()), eq("REJECTED"), any(), any());
    }

    @Test
    void debeTratarUnCodigoMfaEnBlancoComoCodigoFaltante() {
        User admin = buildUser(RoleName.ADMINISTRADOR, true);
        LoginRequest request = loginRequest(admin.getEmail(), "Segura#2026", "   ");

        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Segura#2026", "HASH_ALMACENADO")).thenReturn(true);
        when(mfaService.isEnabled(admin.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.login(request, IP))
                .isInstanceOf(MfaRequiredException.class);
        verify(mfaService, never()).verifyCode(any(), any());
    }

    @Test
    void debeExigirCodigoMfaValidoParaUnAdministradorConMfaActiva() {
        // TC-MFA-13: código erróneo -> el mismo 401 genérico, REJECTED y un fallo contado.
        User admin = buildUser(RoleName.ADMINISTRADOR, true);
        LoginRequest request = loginRequest(admin.getEmail(), "Segura#2026", "000000");

        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Segura#2026", "HASH_ALMACENADO")).thenReturn(true);
        when(mfaService.isEnabled(admin.getId())).thenReturn(true);
        when(mfaService.verifyCode(admin.getId(), "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.login(request, IP))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Las credenciales no son válidas");
        verify(sessionRepository, never()).save(any());
        verify(attemptLimiter).recordFailure(LOGIN_KEY);
        verify(auditService).registerEvent(eq(AuditEventType.LOGIN), eq(admin.getEmail()), eq("REJECTED"), any(), any());
    }

    @Test
    void noDebeRevelarQueHayMfaCuandoLaContrasenaEsInvalida() {
        // TC-MFA-14: contraseña mala + código bueno -> 401 genérico; ni se consulta MFA.
        User admin = buildUser(RoleName.ADMINISTRADOR, true);
        LoginRequest request = loginRequest(admin.getEmail(), "incorrecta", "111111");

        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("incorrecta", "HASH_ALMACENADO")).thenReturn(false);

        assertThatThrownBy(() -> service.login(request, IP))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(mfaService, never()).isEnabled(any());
        verify(mfaService, never()).verifyCode(any(), any());
    }

    @Test
    void debeIgnorarUnCodigoMfaEnviadoPorUnClienteSinMfa() {
        // TC-MFA-15
        User cliente = buildUser(RoleName.CLIENTE, true);
        LoginRequest request = loginRequest(cliente.getEmail(), "Segura#2026", "123456");

        when(userRepository.findByEmailIgnoreCase(cliente.getEmail())).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("Segura#2026", "HASH_ALMACENADO")).thenReturn(true);
        when(jwtTokenProvider.issueToken(cliente.getId().toString())).thenReturn(issuedToken());

        LoginResponse response = service.login(request, IP);

        assertThat(response.getRole()).isEqualTo("CLIENTE");
        verify(mfaService, never()).isEnabled(any());
        verify(mfaService, never()).verifyCode(any(), any());
    }

    @Test
    void debePermitirLoginDeAdministradorSinMfaActivaTodaviaSinExigirCodigo() {
        // Admin recién ascendido (HU-05): triggerMandatorySetup ya creó una
        // fila Mfa pendiente, pero isEnabled() sigue false hasta que el
        // usuario complete /mfa/activate -> puede iniciar sesión, pero
        // MfaEnrollmentFilter solo le dejará usar /auth/mfa/** y logout.
        User admin = buildUser(RoleName.ADMINISTRADOR, true);
        LoginRequest request = loginRequest(admin.getEmail(), "Segura#2026", null);

        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Segura#2026", "HASH_ALMACENADO")).thenReturn(true);
        when(mfaService.isEnabled(admin.getId())).thenReturn(false);
        when(jwtTokenProvider.issueToken(admin.getId().toString())).thenReturn(issuedToken());

        LoginResponse response = service.login(request, IP);

        assertThat(response.getRole()).isEqualTo("ADMINISTRADOR");
        verify(mfaService, never()).verifyCode(any(), any());
    }

    // --- HU-04: logout ---

    @Test
    void logoutDebeRevocarTodasLasSesionesYAuditarElEvento() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(RoleName.CLIENTE, true)));

        service.logout(userId, IP);

        verify(sessionRepository).revokeAllByUserId(userId);
        verify(auditService).registerEvent(eq(AuditEventType.LOGOUT), any(), eq("SUCCESS"), any(), any());
    }
}
