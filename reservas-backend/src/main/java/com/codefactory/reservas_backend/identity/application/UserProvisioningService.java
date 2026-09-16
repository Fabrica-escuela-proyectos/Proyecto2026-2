package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.identity.domain.RoleName;

import java.util.UUID;

/**
 * Contrato que Identity & Access expone a otros módulos que necesitan crear
 * una cuenta de acceso con un rol distinto de CLIENTE (hoy: Provider en
 * HU-03), sin darles acceso directo a UserRepository/RoleRepository
 * (ADR-003-modularidad-e-interfaces.md: "Reservation no deberá acceder
 * directamente al UserRepository de Identity... en su lugar podrá utilizar
 * una interfaz o servicio").
 *
 * Se mantiene separado de UserRegistrationService (que sigue siendo
 * exclusivamente el caso de uso de HU-01: registro público con rol CLIENTE
 * fijo) para no tocar código ya probado de HU-01 al agregar HU-03.
 */
public interface UserProvisioningService {

    ProvisionedUser provisionUser(String fullName, String email, String cellphone, String rawPassword, RoleName role);

    record ProvisionedUser(UUID id, String fullName, String email, String cellphone, RoleName role) {
    }
}
