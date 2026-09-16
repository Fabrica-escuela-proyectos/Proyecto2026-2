package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.controller.dto.LoginRequest;
import com.codefactory.reservas_backend.identity.controller.dto.LoginResponse;
import com.codefactory.reservas_backend.identity.domain.InvalidCredentialsException;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.Session;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.SessionRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import com.codefactory.reservas_backend.identity.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Implementa HU-02 (Inicio de sesión) y HU-04 (Cerrar sesión), siguiendo el
 * flujo de ADR-002-autenticacion-y-sesiones.md sección 3: valida
 * credenciales, exige MFA cuando corresponde, emite el JWT (JwtTokenProvider,
 * ya existente) y persiste el control de sesión (Session/SessionRepository,
 * ya existentes) para poder revocarla antes de que expire por sí sola.
 *
 * No usa Spring Security AuthenticationManager/UserDetailsService: sigue el
 * mismo estilo manual y explícito que ya usa UserRegistrationService para
 * HU-01 (buscar, comparar hash, decidir), en vez de introducir una capa de
 * abstracción de Spring Security que el resto del proyecto no usa todavía.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SessionRepository sessionRepository;
    private final MfaService mfaService;
    private final AuditService auditService;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String originIp) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .filter(u -> passwordEncoder.matches(request.getPassword(), u.getPasswordHash()))
                .filter(User::isEnabled)
                .orElse(null);

        // Escenario "Intento de inicio de sesión con credenciales inválidas":
        // un solo mensaje genérico tanto para correo inexistente, contraseña
        // incorrecta o cuenta deshabilitada (errores-api-sprint-1.md sección
        // 5: no revelar cuál de las validaciones falló).
        if (user == null) {
            auditService.registerEvent(AuditEventType.LOGIN, request.getEmail(), "REJECTED", "credenciales inválidas", originIp);
            throw new InvalidCredentialsException("Las credenciales no son válidas");
        }

        String role = primaryRole(user);

        // Escenario "Verificación adicional para cuentas administrativas":
        // solo se exige el código si la cuenta ya completó el enrolamiento
        // de MFA (mfaService.isEnabled) — evita el bloqueo de un admin
        // recién ascendido que todavía no ha llamado a /mfa/activate (ver
        // MfaServiceImpl.triggerMandatorySetup).
        if (RoleName.ADMINISTRADOR.name().equals(role)
                && mfaService.isEnabled(user.getId())
                && !mfaService.verifyCode(user.getId(), request.getMfaCode())) {
            auditService.registerEvent(AuditEventType.LOGIN, user.getEmail(), "REJECTED", "código MFA inválido", originIp);
            throw new InvalidCredentialsException("Las credenciales no son válidas");
        }

        JwtTokenProvider.IssuedToken issued = jwtTokenProvider.issueToken(user.getId().toString());

        Session session = Session.builder()
                .userId(user.getId())
                .tokenId(issued.tokenId())
                .expiresAt(issued.expiresAt())
                .build();
        sessionRepository.save(session);

        // Escenario "Protección de datos sensibles en los registros internos
        // del sistema": ni la contraseña ni el JWT completo se auditan,
        // solo el resultado del intento.
        auditService.registerEvent(AuditEventType.LOGIN, user.getEmail(), "SUCCESS", null, originIp);

        long expiresIn = Duration.between(Instant.now(), issued.expiresAt()).toSeconds();
        return new LoginResponse(issued.token(), expiresIn, role);
    }

    @Override
    @Transactional
    public void logout(UUID userId, String originIp) {
        sessionRepository.revokeAllByUserId(userId);

        String email = userRepository.findById(userId).map(User::getEmail).orElse(null);
        auditService.registerEvent(AuditEventType.LOGOUT, email, "SUCCESS", null, originIp);
    }

    private String primaryRole(User user) {
        return user.getRoles().stream()
                .findFirst()
                .map(r -> r.getName().name())
                .orElse(null);
    }
}
