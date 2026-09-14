package com.codefactory.reservas_backend.identity.application;

import java.util.UUID;

/**
 * Representación mínima del usuario autenticado que IdentityService expone
 * a otros módulos (interfaces-modulos-sprint-1.md sección 2), sin filtrar
 * la entidad interna identity.domain.User.
 */
public record UserIdentity(UUID id, String email, String role) {
}
