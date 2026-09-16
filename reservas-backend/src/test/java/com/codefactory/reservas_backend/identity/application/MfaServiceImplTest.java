package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.controller.dto.MfaSetupResponse;
import com.codefactory.reservas_backend.identity.domain.InvalidMfaCodeException;
import com.codefactory.reservas_backend.identity.domain.Mfa;
import com.codefactory.reservas_backend.identity.domain.MfaNotConfiguredException;
import com.codefactory.reservas_backend.identity.infrastructure.MfaRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import com.codefactory.reservas_backend.identity.infrastructure.security.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre HU-02 (verificación adicional para cuentas administrativas) y HU-05
 * ("se activa un evento obligatorio, para la creación de MFA para el nuevo
 * Administrador").
 */
@ExtendWith(MockitoExtension.class)
class MfaServiceImplTest {

    @Mock
    private MfaRepository mfaRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TotpService totpService;
    @Mock
    private AuditService auditService;

    private MfaServiceImpl service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new MfaServiceImpl(mfaRepository, userRepository, totpService, auditService);
        userId = UUID.randomUUID();
    }

    @Test
    void setupDebeCrearUnSecretoPendienteSiNoExisteUnoYDevolverLaUriOtpauth() {
        when(mfaRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(totpService.generateSecret()).thenReturn("SECRETOBASE32");
        when(mfaRepository.save(any(Mfa.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        MfaSetupResponse response = service.setup(userId);

        assertThat(response.isAlreadyEnabled()).isFalse();
        assertThat(response.getOtpauthUri()).contains("SECRETOBASE32");
    }

    @Test
    void setupNoDebeReexponerElSecretoSiLaMfaYaEstaActiva() {
        Mfa mfaActiva = Mfa.builder().userId(userId).secret("SECRETOBASE32").enabled(true).build();
        when(mfaRepository.findByUserId(userId)).thenReturn(Optional.of(mfaActiva));

        MfaSetupResponse response = service.setup(userId);

        assertThat(response.isAlreadyEnabled()).isTrue();
        assertThat(response.getOtpauthUri()).isNull();
    }

    @Test
    void activateDebeFallarSiNoHayUnaConfiguracionPendiente() {
        when(mfaRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(userId, "123456"))
                .isInstanceOf(MfaNotConfiguredException.class);
    }

    @Test
    void activateDebeRechazarUnCodigoInvalido() {
        Mfa pendiente = Mfa.builder().userId(userId).secret("SECRETOBASE32").enabled(false).build();
        when(mfaRepository.findByUserId(userId)).thenReturn(Optional.of(pendiente));
        when(totpService.verify("SECRETOBASE32", "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.activate(userId, "000000"))
                .isInstanceOf(InvalidMfaCodeException.class);
        verify(mfaRepository, never()).save(any());
    }

    @Test
    void activateDebeMarcarLaMfaComoActivaConUnCodigoValido() {
        Mfa pendiente = Mfa.builder().userId(userId).secret("SECRETOBASE32").enabled(false).build();
        when(mfaRepository.findByUserId(userId)).thenReturn(Optional.of(pendiente));
        when(totpService.verify("SECRETOBASE32", "111111")).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        service.activate(userId, "111111");

        assertThat(pendiente.isEnabled()).isTrue();
        verify(mfaRepository).save(pendiente);
        verify(auditService).registerEvent(eq(AuditEventType.ACTIVACION_MFA), any(), eq("SUCCESS"), anyString(), any());
    }

    @Test
    void isEnabledDebeSerFalsoCuandoNoHayConfiguracion() {
        when(mfaRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(service.isEnabled(userId)).isFalse();
    }

    @Test
    void verifyCodeDebeSerFalsoSiLaMfaNoEstaActivaAunqueElCodigoSeaCorrecto() {
        Mfa pendiente = Mfa.builder().userId(userId).secret("SECRETOBASE32").enabled(false).build();
        when(mfaRepository.findByUserId(userId)).thenReturn(Optional.of(pendiente));

        assertThat(service.verifyCode(userId, "111111")).isFalse();
    }

    @Test
    void triggerMandatorySetupNoDebeSobrescribirUnaConfiguracionExistente() {
        Mfa existente = Mfa.builder().userId(userId).secret("YAEXISTE").enabled(false).build();
        when(mfaRepository.findByUserId(userId)).thenReturn(Optional.of(existente));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        service.triggerMandatorySetup(userId);

        verify(mfaRepository, never()).save(any());
        verify(auditService).registerEvent(eq(AuditEventType.ACTIVACION_MFA), any(), eq("PENDING"), anyString(), any());
    }

    @Test
    void triggerMandatorySetupDebeCrearUnaConfiguracionPendienteSiNoExiste() {
        when(mfaRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(totpService.generateSecret()).thenReturn("NUEVOSECRETO");
        when(mfaRepository.save(any(Mfa.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        service.triggerMandatorySetup(userId);

        verify(mfaRepository).save(org.mockito.ArgumentMatchers.argThat(mfa ->
                mfa.getUserId().equals(userId) && !mfa.isEnabled()));
    }
}
