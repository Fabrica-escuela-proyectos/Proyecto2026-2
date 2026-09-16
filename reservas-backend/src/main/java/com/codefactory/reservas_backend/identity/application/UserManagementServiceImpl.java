package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.controller.dto.ChangeUserRoleResponse;
import com.codefactory.reservas_backend.identity.controller.dto.UserResponse;
import com.codefactory.reservas_backend.identity.domain.AdminDeletionNotAllowedException;
import com.codefactory.reservas_backend.identity.domain.ProviderRoleImmutableException;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.RoleNotFoundException;
import com.codefactory.reservas_backend.identity.domain.SelfModificationException;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.domain.UserNotFoundException;
import com.codefactory.reservas_backend.identity.infrastructure.RoleRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Implementa HU-05 (Gestionar roles y permisos) y el ejemplo de endpoint
 * protegido de HU-06. Cada regla de negocio está trazada 1:1 a un escenario
 * Gherkin de HU 05 - Gestionar roles y permisos.txt.
 */
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;
    private final MfaService mfaService;

    @Override
    public UserResponse getUser(UUID targetUserId, UserIdentity requester) {
        // HU-06: "Cliente/Proveedor gestiona únicamente sus propias
        // reservas y datos" / "intenta gestionar... de otro usuario -> el
        // sistema debe denegar el acceso".
        boolean isAdmin = RoleName.ADMINISTRADOR.name().equals(requester.role());
        if (!isAdmin && !requester.id().equals(targetUserId)) {
            throw new AccessDeniedException("No tiene acceso a la información de otro usuario");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("El usuario solicitado no existe"));
        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public ChangeUserRoleResponse changeRole(UUID targetUserId, String requestedRoleRaw, UserIdentity admin, String originIp) {
        // Escenario "Usuario intenta modificar sus propios permisos".
        if (admin.id().equals(targetUserId)) {
            auditService.registerEvent(AuditEventType.CAMBIO_ROL, admin.email(), "REJECTED",
                    "intento de auto-modificación de rol", originIp);
            throw new SelfModificationException("No puede modificar su propio rol");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("El usuario solicitado no existe"));

        // Escenario "Administrador intenta cambiar un rol Proveedor de un
        // usuario existente": inmutable sin importar el rol solicitado.
        boolean targetIsProvider = target.getRoles().stream().anyMatch(r -> r.getName() == RoleName.PROVEEDOR);
        if (targetIsProvider) {
            auditService.registerEvent(AuditEventType.CAMBIO_ROL, target.getEmail(), "REJECTED",
                    "el rol de un usuario Proveedor no se puede modificar", originIp);
            throw new ProviderRoleImmutableException("No se permite modificar el rol de un usuario con rol Proveedor");
        }

        // Escenario "Administrador intenta asignar un rol inexistente".
        RoleName newRoleName;
        try {
            newRoleName = RoleName.valueOf(requestedRoleRaw.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            auditService.registerEvent(AuditEventType.CAMBIO_ROL, target.getEmail(), "REJECTED",
                    "rol inexistente solicitado: " + requestedRoleRaw, originIp);
            throw new RoleNotFoundException("El rol solicitado no existe");
        }

        Role newRole = roleRepository.findByName(newRoleName)
                .orElseThrow(() -> new RoleNotFoundException("El rol solicitado no existe"));

        // HashSet mutable, no Set.of(): target ya es una entidad gestionada
        // (viene de findById), así que save() pasa por
        // EntityManager.merge(), y el algoritmo de merge de colecciones de
        // Hibernate intenta hacer clear() sobre el valor asignado para
        // reconciliarlo con la colección persistente — un Set.of()
        // inmutable revienta ahí con UnsupportedOperationException.
        target.setRoles(new HashSet<>(Set.of(newRole)));
        userRepository.save(target);

        auditService.registerEvent(AuditEventType.CAMBIO_ROL, target.getEmail(), "SUCCESS",
                "nuevo rol: " + newRoleName, originIp);

        // Escenario "Administrador asigna un rol Administrador a un usuario
        // existente": "se activa un evento obligatorio, para la creación de
        // MFA para el nuevo Administrador".
        if (newRoleName == RoleName.ADMINISTRADOR) {
            mfaService.triggerMandatorySetup(target.getId());
        }

        return new ChangeUserRoleResponse(target.getId(), target.getEmail(), newRoleName.name());
    }

    @Override
    @Transactional
    public void deleteUser(UUID targetUserId, UserIdentity admin, String originIp) {
        if (admin.id().equals(targetUserId)) {
            throw new SelfModificationException("No puede eliminar su propia cuenta");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("El usuario solicitado no existe"));

        boolean targetIsAdmin = target.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMINISTRADOR);
        if (targetIsAdmin) {
            throw new AdminDeletionNotAllowedException(
                    "No se permite eliminar cuentas con rol Administrador mediante este endpoint");
        }

        String email = target.getEmail();
        String deletedRole = target.getRoles().stream().findFirst().map(r -> r.getName().name()).orElse(null);

        // El usuario se elimina en su totalidad (cascada a user_roles,
        // sessions, y a providers/businesses si tenía rol Proveedor - ver
        // V3__create_provider_schema.sql). audit_logs no tiene FK hacia
        // users (guarda subject_email como texto), así que el historial de
        // auditoría sobrevive intacto: así se cumple "guardando historial
        // de las funcionalidades de usuario" del escenario Gherkin.
        userRepository.delete(target);

        auditService.registerEvent(AuditEventType.ELIMINACION_USUARIO, email, "SUCCESS",
                "usuario eliminado, rol: " + deletedRole, originIp);
    }
}
