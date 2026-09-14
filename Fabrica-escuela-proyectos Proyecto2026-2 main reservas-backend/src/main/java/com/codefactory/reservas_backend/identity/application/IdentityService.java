package com.codefactory.reservas_backend.identity.application;

import java.util.Optional;

/**
 * Contrato que Identity & Access expone al resto del monolito
 * (interfaces-modulos-sprint-1.md sección 2 y ADR-003-modularidad-e-interfaces.md):
 * permite que otros módulos (Provider, Reservation, ...) consulten al
 * usuario autenticado sin acceder directamente a UserRepository ni a la
 * entidad User.
 */
public interface IdentityService {

    Optional<UserIdentity> getCurrentUser();

    boolean isAuthenticated();
}
