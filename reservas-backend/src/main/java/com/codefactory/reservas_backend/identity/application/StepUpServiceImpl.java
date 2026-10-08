package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.domain.InvalidCredentialsException;
import com.codefactory.reservas_backend.identity.domain.MfaEnrollmentRequiredException;
import com.codefactory.reservas_backend.identity.domain.MfaRequiredException;
import com.codefactory.reservas_backend.identity.infrastructure.AuthAttemptLimiter;
import com.codefactory.reservas_backend.identity.infrastructure.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Implementa el step-up de la política de MFA (ADR-004, P6 y P7). No es
 * transaccional a propósito: cada evento de auditoría se confirma por su
 * cuenta, de modo que los rechazos quedan registrados aunque la operación
 * termine en error. Nunca se audita ni se loguea el código recibido.
 */
@Service
@RequiredArgsConstructor
public class StepUpServiceImpl implements StepUpService {

    private final MfaService mfaService;
    private final AuthAttemptLimiter attemptLimiter;
    private final AuditService auditService;

    @Override
    public void requireValidCode(UserIdentity actor, String mfaCode, String operation, String originIp) {
        String attemptKey = AuthAttemptLimiter.stepUpKey(actor.id());

        if (attemptLimiter.isBlocked(attemptKey)) {
            audit(actor, "REJECTED", "bloqueado por intentos fallidos de MFA: " + operation, originIp);
            throw new TooManyRequestsException("Demasiados intentos fallidos de verificación. Intente más tarde.");
        }
        if (!mfaService.isEnabled(actor.id())) {
            throw new MfaEnrollmentRequiredException();
        }
        if (mfaCode == null || mfaCode.isBlank()) {
            audit(actor, "REJECTED", "falta el código MFA: " + operation, originIp);
            throw new MfaRequiredException("Esta operación requiere el código de verificación MFA (header "
                    + MFA_CODE_HEADER + ")");
        }
        if (!mfaService.verifyCode(actor.id(), mfaCode)) {
            attemptLimiter.recordFailure(attemptKey);
            audit(actor, "REJECTED", "código MFA inválido: " + operation, originIp);
            throw new InvalidCredentialsException("El código de verificación no es válido");
        }

        attemptLimiter.reset(attemptKey);
        audit(actor, "SUCCESS", "verificación adicional superada: " + operation, originIp);
    }

    private void audit(UserIdentity actor, String outcome, String detail, String originIp) {
        auditService.registerEvent(AuditEventType.OPERACION_SENSIBLE, actor.email(), outcome, detail, originIp);
    }
}
