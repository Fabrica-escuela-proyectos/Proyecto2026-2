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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MfaServiceImpl implements MfaService {

    private static final String ISSUER = "ReservasPlataforma";

    private final MfaRepository mfaRepository;
    private final UserRepository userRepository;
    private final TotpService totpService;
    private final AuditService auditService;

    @Override
    @Transactional
    public MfaSetupResponse setup(UUID userId) {
        Mfa mfa = mfaRepository.findByUserId(userId)
                .orElseGet(() -> mfaRepository.save(Mfa.builder()
                        .userId(userId)
                        .secret(totpService.generateSecret())
                        .build()));

        if (mfa.isEnabled()) {
            return new MfaSetupResponse(null, true);
        }

        String email = userRepository.findById(userId).map(u -> u.getEmail()).orElse(userId.toString());
        String otpauthUri = "otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=6&period=30"
                .formatted(ISSUER, email, mfa.getSecret(), ISSUER);
        return new MfaSetupResponse(otpauthUri, false);
    }

    @Override
    @Transactional
    public void activate(UUID userId, String code) {
        Mfa mfa = mfaRepository.findByUserId(userId)
                .orElseThrow(() -> new MfaNotConfiguredException(
                        "Debe iniciar la configuración de MFA (POST /api/v1/auth/mfa/setup) antes de activarla"));

        if (!totpService.verify(mfa.getSecret(), code)) {
            throw new InvalidMfaCodeException("El código de verificación no es válido");
        }

        mfa.setEnabled(true);
        mfaRepository.save(mfa);

        String email = userRepository.findById(userId).map(u -> u.getEmail()).orElse(null);
        auditService.registerEvent(AuditEventType.ACTIVACION_MFA, email, "SUCCESS", "MFA activada por el usuario", null);
    }

    @Override
    public boolean isEnabled(UUID userId) {
        return mfaRepository.findByUserId(userId).map(Mfa::isEnabled).orElse(false);
    }

    @Override
    public boolean verifyCode(UUID userId, String code) {
        return mfaRepository.findByUserId(userId)
                .filter(Mfa::isEnabled)
                .map(mfa -> totpService.verify(mfa.getSecret(), code))
                .orElse(false);
    }

    @Override
    @Transactional
    public void triggerMandatorySetup(UUID userId) {
        mfaRepository.findByUserId(userId).orElseGet(() -> mfaRepository.save(Mfa.builder()
                .userId(userId)
                .secret(totpService.generateSecret())
                .build()));

        String email = userRepository.findById(userId).map(u -> u.getEmail()).orElse(null);
        auditService.registerEvent(AuditEventType.ACTIVACION_MFA, email, "PENDING",
                "configuración de MFA obligatoria tras ascenso a ADMINISTRADOR", null);
    }
}
