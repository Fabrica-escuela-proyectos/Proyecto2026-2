package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implementación mínima de AuthorizationService para Sprint 1.
 *
 * {@link #hasRole} sí tiene datos reales para responder: la tabla
 * user_roles ya existe (HU-01 la usa para asignar CLIENTE).
 *
 * {@link #hasPermission} lanza {@link UnsupportedOperationException} a
 * propósito en vez de devolver {@code true}/{@code false} de forma
 * silenciosa: Sprint 1 no define ningún catálogo de permisos granular
 * (dtos-sprint-1.md y endpoints-sprint-1.md solo hablan de roles), así que
 * cualquier llamado real a este método hoy estaría verificando una regla
 * que el equipo todavía no ha decidido. Devolver un booleano por defecto
 * aquí podría esconder un hueco de seguridad real una vez HU05/HU06
 * empiecen a depender de este contrato.
 */
@Service
@RequiredArgsConstructor
public class AuthorizationServiceImpl implements AuthorizationService {

    private final UserRepository userRepository;

    @Override
    public boolean hasRole(UUID userId, String role) {
        return userRepository.findById(userId)
                .map(user -> user.getRoles().stream()
                        .anyMatch(r -> r.getName().name().equalsIgnoreCase(role)))
                .orElse(false);
    }

    @Override
    public boolean hasPermission(UUID userId, String permission) {
        throw new UnsupportedOperationException(
                "El catálogo de permisos granular aún no está definido en Sprint 1 (solo se modelan roles). "
                        + "Pendiente de HU05/HU06 y de validación con el equipo — ver docs/matriz-actualizaciones.md.");
    }
}
