package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.identity.controller.dto.ChangeUserRoleResponse;
import com.codefactory.reservas_backend.identity.controller.dto.UserResponse;

import java.util.UUID;

/**
 * HU-05 (Gestionar roles y permisos) y el ejemplo de endpoint protegido de
 * HU-06 (GET /api/v1/users/{userId}, endpoints-sprint-1.md sección 7).
 */
public interface UserManagementService {

    /**
     * HU-06: un Cliente/Proveedor solo puede consultarse a sí mismo; un
     * Administrador puede consultar a cualquiera.
     */
    UserResponse getUser(UUID targetUserId, UserIdentity requester);

    /**
     * HU-05: cambia el rol de {@code targetUserId}. {@code requestedRole}
     * llega como String (tal como lo define ChangeUserRoleRequest) porque
     * "rol inexistente" es en sí mismo uno de los escenarios a rechazar.
     */
    ChangeUserRoleResponse changeRole(UUID targetUserId, String requestedRole, UserIdentity admin, String originIp);

    /**
     * HU-05, escenario "administrador elimina usuario con rol".
     */
    void deleteUser(UUID targetUserId, UserIdentity admin, String originIp);
}
