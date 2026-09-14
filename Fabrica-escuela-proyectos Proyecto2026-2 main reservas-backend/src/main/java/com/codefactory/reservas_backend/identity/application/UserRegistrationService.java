package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.controller.dto.RegisterUserRequest;
import com.codefactory.reservas_backend.identity.controller.dto.RegisterUserResponse;
import com.codefactory.reservas_backend.identity.domain.DuplicateEmailException;
import com.codefactory.reservas_backend.identity.domain.DuplicatePhoneException;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.RegistrationRateLimiter;
import com.codefactory.reservas_backend.identity.infrastructure.RoleRepository;
import com.codefactory.reservas_backend.identity.infrastructure.TooManyRequestsException;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Implementa HU-01 - Registrar cliente (la parte de Simon dentro del
 * backend). Coordina el caso de uso: valida reglas, delega persistencia a
 * infrastructure y auditoría al módulo Audit vía su interfaz expuesta
 * (ADR-003-modularidad-e-interfaces.md), nunca contra su repositorio.
 *
 * Cada regla está trazada a un escenario Gherkin concreto de
 * HU-01-Registrar-cliente.txt; ver la tabla de mapeo en
 * docs/HU-01-checklist.md.
 */
@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final RegistrationRateLimiter rateLimiter;

    @Transactional
    public RegisterUserResponse register(RegisterUserRequest request, String originIp) {

        // Escenario: "Bloqueo temporal por múltiples intentos de registro"
        if (rateLimiter.isBlocked(originIp)) {
            throw new TooManyRequestsException(
                    "Se han detectado demasiadas solicitudes de registro desde este origen. Intenta más tarde.");
        }
        rateLimiter.registerAttempt(originIp);

        // Escenario: "Registro con correo ya existente"
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            auditService.registerEvent(AuditEventType.REGISTRO_USUARIO, request.getEmail(), "REJECTED",
                    "correo ya en uso", originIp);
            throw new DuplicateEmailException("El correo electrónico ya está en uso");
        }

        // Escenario: "Registro con número de celular ya registrado en otra cuenta"
        if (userRepository.existsByCellphone(request.getCellphone())) {
            auditService.registerEvent(AuditEventType.REGISTRO_USUARIO, request.getEmail(), "REJECTED",
                    "celular ya en uso", originIp);
            throw new DuplicatePhoneException("El número de celular ya está en uso");
        }

        // El rol "Cliente" se asigna SIEMPRE por el servidor, nunca desde el
        // payload del cliente HTTP (regla de negocio explícita de HU-01,
        // confirmada en dtos-sprint-1.md sección 11 y endpoints-sprint-1.md
        // sección 2).
        Role clienteRole = roleRepository.findByName(RoleName.CLIENTE)
                .orElseThrow(() -> new IllegalStateException(
                        "El rol CLIENTE no está inicializado en la base de datos. "
                                + "Verificar el seed de V1__create_identity_schema.sql"));

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .cellphone(request.getCellphone())
                // Escenario: "La información del usuario queda protegida en todo
                // momento" - solo se persiste el hash, nunca la contraseña en
                // texto plano y nunca se loguea el valor original.
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(clienteRole))
                .build();

        User saved = userRepository.save(user);

        // Escenario: "Registro exitoso con datos válidos"
        auditService.registerEvent(AuditEventType.REGISTRO_USUARIO, saved.getEmail(), "SUCCESS", null, originIp);

        return new RegisterUserResponse(saved.getId(), saved.getFullName(), saved.getEmail(),
                clienteRole.getName().name());
    }
}
