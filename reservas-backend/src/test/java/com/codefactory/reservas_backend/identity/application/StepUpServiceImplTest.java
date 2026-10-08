package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.domain.InvalidCredentialsException;
import com.codefactory.reservas_backend.identity.domain.MfaEnrollmentRequiredException;
import com.codefactory.reservas_backend.identity.domain.MfaRequiredException;
import com.codefactory.reservas_backend.identity.infrastructure.AuthAttemptLimiter;
import com.codefactory.reservas_backend.identity.infrastructure.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Confirmación adicional en operaciones sensibles (HU-02, "Verificación
 * adicional para operaciones sensibles"; ADR-004 P6/P7; TC-MFA-18..20).
 */
@ExtendWith(MockitoExtension.class)
class StepUpServiceImplTest {

    private static final String IP = "127.0.0.1";
    private static final String OPERACION = "CAMBIO_ROL";

    @Mock
    private MfaService mfaService;
    @Mock
    private AuthAttemptLimiter attemptLimiter;
    @Mock
    private AuditService auditService;

    private StepUpServiceImpl service;
    private UserIdentity admin;
    private String attemptKey;

    @BeforeEach
    void setUp() {
        service = new StepUpServiceImpl(mfaService, attemptLimiter, auditService);
        admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", "ADMINISTRADOR");
        attemptKey = AuthAttemptLimiter.stepUpKey(admin.id());
    }

    @Test
    void debePermitirLaOperacionConUnCodigoValidoYAuditarlo() {
        when(mfaService.isEnabled(admin.id())).thenReturn(true);
        when(mfaService.verifyCode(admin.id(), "123456")).thenReturn(true);

        assertThatCode(() -> service.requireValidCode(admin, "123456", OPERACION, IP)).doesNotThrowAnyException();

        verify(attemptLimiter).reset(attemptKey);
        verify(auditService).registerEvent(eq(AuditEventType.OPERACION_SENSIBLE), eq("admin@example.com"),
                eq("SUCCESS"), contains(OPERACION), eq(IP));
    }

    @Test
    void debePedirElCodigoCuandoNoLlegaYNoContarloComoFallo() {
        when(mfaService.isEnabled(admin.id())).thenReturn(true);

        assertThatThrownBy(() -> service.requireValidCode(admin, null, OPERACION, IP))
                .isInstanceOf(MfaRequiredException.class)
                .hasMessageContaining("X-MFA-Code");

        verify(mfaService, never()).verifyCode(any(), any());
        verify(attemptLimiter, never()).recordFailure(any());
        verify(auditService).registerEvent(eq(AuditEventType.OPERACION_SENSIBLE), any(), eq("REJECTED"), any(), any());
    }

    @Test
    void debeTratarUnCodigoEnBlancoComoCodigoFaltante() {
        when(mfaService.isEnabled(admin.id())).thenReturn(true);

        assertThatThrownBy(() -> service.requireValidCode(admin, "  ", OPERACION, IP))
                .isInstanceOf(MfaRequiredException.class);
    }

    @Test
    void debeRechazarUnCodigoIncorrectoContarElFalloYAuditarlo() {
        when(mfaService.isEnabled(admin.id())).thenReturn(true);
        when(mfaService.verifyCode(admin.id(), "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.requireValidCode(admin, "000000", OPERACION, IP))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(attemptLimiter).recordFailure(attemptKey);
        verify(attemptLimiter, never()).reset(any());
        verify(auditService).registerEvent(eq(AuditEventType.OPERACION_SENSIBLE), any(), eq("REJECTED"), any(), any());
    }

    @Test
    void debeResponder429SinVerificarElCodigoCuandoHayDemasiadosFallos() {
        // TC-MFA-21: tras 5 códigos incorrectos seguidos, bloqueo temporal.
        when(attemptLimiter.isBlocked(attemptKey)).thenReturn(true);

        assertThatThrownBy(() -> service.requireValidCode(admin, "123456", OPERACION, IP))
                .isInstanceOf(TooManyRequestsException.class);

        verify(mfaService, never()).verifyCode(any(), any());
    }

    @Test
    void debeExigirElEnrolamientoSiElAdministradorNoTieneMfaActiva() {
        when(mfaService.isEnabled(admin.id())).thenReturn(false);

        assertThatThrownBy(() -> service.requireValidCode(admin, "123456", OPERACION, IP))
                .isInstanceOf(MfaEnrollmentRequiredException.class);

        verify(mfaService, never()).verifyCode(any(), any());
    }

    @Test
    void nuncaDebeAuditarNiRegistrarElCodigoRecibido() {
        when(mfaService.isEnabled(admin.id())).thenReturn(true);
        when(mfaService.verifyCode(admin.id(), "987654")).thenReturn(false);

        assertThatThrownBy(() -> service.requireValidCode(admin, "987654", OPERACION, IP))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageNotContaining("987654");

        verify(auditService).registerEvent(any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(detail -> detail != null && !detail.contains("987654")), any());
    }
}
