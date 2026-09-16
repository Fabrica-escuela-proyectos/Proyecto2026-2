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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de UserManagementServiceImpl, mapeadas a los escenarios
 * Gherkin de HU 05 - Gestionar roles y permisos.txt y al ejemplo de
 * endpoint protegido de HU 06 - Acceso segun rol.txt.
 */
@ExtendWith(MockitoExtension.class)
class UserManagementServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private MfaService mfaService;

    private UserManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserManagementServiceImpl(userRepository, roleRepository, auditService, mfaService);
    }

    private User buildUser(UUID id, RoleName roleName) {
        Role role = Role.builder().id(UUID.randomUUID()).name(roleName).build();
        return User.builder().id(id).email(id + "@example.com").roles(Set.of(role)).build();
    }

    // --- getUser (HU-06) ---

    @Test
    void getUserDebePermitirQueUnUsuarioConsulteSuPropiaInformacion() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, RoleName.CLIENTE);
        UserIdentity requester = new UserIdentity(userId, user.getEmail(), "CLIENTE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse response = service.getUser(userId, requester);

        assertThat(response.getId()).isEqualTo(userId);
    }

    @Test
    void getUserDebePermitirQueUnAdministradorConsulteACualquierUsuario() {
        UUID targetId = UUID.randomUUID();
        User target = buildUser(targetId, RoleName.CLIENTE);
        UserIdentity admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", "ADMINISTRADOR");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        UserResponse response = service.getUser(targetId, admin);

        assertThat(response.getId()).isEqualTo(targetId);
    }

    @Test
    void getUserDebeDenegarElAccesoAInformacionDeOtroUsuario() {
        UUID targetId = UUID.randomUUID();
        UserIdentity requester = new UserIdentity(UUID.randomUUID(), "otro@example.com", "CLIENTE");

        assertThatThrownBy(() -> service.getUser(targetId, requester))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void getUserDebeFallarSiElUsuarioObjetivoNoExiste() {
        UUID targetId = UUID.randomUUID();
        UserIdentity admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", "ADMINISTRADOR");
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser(targetId, admin))
                .isInstanceOf(UserNotFoundException.class);
    }

    // --- changeRole (HU-05) ---

    @Test
    void changeRoleDebeRechazarQueUnAdministradorSeAutoModifique() {
        UUID adminId = UUID.randomUUID();
        UserIdentity admin = new UserIdentity(adminId, "admin@example.com", "ADMINISTRADOR");

        assertThatThrownBy(() -> service.changeRole(adminId, "PROVEEDOR", admin, "127.0.0.1"))
                .isInstanceOf(SelfModificationException.class);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void changeRoleDebeFallarSiElUsuarioObjetivoNoExiste() {
        UUID targetId = UUID.randomUUID();
        UserIdentity admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", "ADMINISTRADOR");
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole(targetId, "ADMINISTRADOR", admin, "127.0.0.1"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void changeRoleDebeRechazarModificarElRolDeUnProveedor() {
        UUID targetId = UUID.randomUUID();
        User proveedor = buildUser(targetId, RoleName.PROVEEDOR);
        UserIdentity admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", "ADMINISTRADOR");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(proveedor));

        assertThatThrownBy(() -> service.changeRole(targetId, "CLIENTE", admin, "127.0.0.1"))
                .isInstanceOf(ProviderRoleImmutableException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changeRoleDebeRechazarUnRolInexistente() {
        UUID targetId = UUID.randomUUID();
        User cliente = buildUser(targetId, RoleName.CLIENTE);
        UserIdentity admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", "ADMINISTRADOR");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(cliente));

        assertThatThrownBy(() -> service.changeRole(targetId, "SUPERUSUARIO", admin, "127.0.0.1"))
                .isInstanceOf(RoleNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changeRoleDebeActualizarElRolYAuditarElCambio() {
        UUID targetId = UUID.randomUUID();
        User cliente = buildUser(targetId, RoleName.CLIENTE);
        UserIdentity admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", "ADMINISTRADOR");
        Role proveedorRole = Role.builder().id(UUID.randomUUID()).name(RoleName.PROVEEDOR).build();

        when(userRepository.findById(targetId)).thenReturn(Optional.of(cliente));
        when(roleRepository.findByName(RoleName.PROVEEDOR)).thenReturn(Optional.of(proveedorRole));

        ChangeUserRoleResponse response = service.changeRole(targetId, "proveedor", admin, "127.0.0.1");

        assertThat(response.getRole()).isEqualTo("PROVEEDOR");
        assertThat(cliente.getRoles()).containsExactly(proveedorRole);
        verify(auditService).registerEvent(eq(AuditEventType.CAMBIO_ROL), eq(cliente.getEmail()), eq("SUCCESS"), any(), any());
        verify(mfaService, never()).triggerMandatorySetup(any());
    }

    @Test
    void changeRoleDebeActivarElEventoObligatorioDeMfaAlAscenderAAdministrador() {
        UUID targetId = UUID.randomUUID();
        User cliente = buildUser(targetId, RoleName.CLIENTE);
        UserIdentity admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", "ADMINISTRADOR");
        Role adminRole = Role.builder().id(UUID.randomUUID()).name(RoleName.ADMINISTRADOR).build();

        when(userRepository.findById(targetId)).thenReturn(Optional.of(cliente));
        when(roleRepository.findByName(RoleName.ADMINISTRADOR)).thenReturn(Optional.of(adminRole));

        service.changeRole(targetId, "ADMINISTRADOR", admin, "127.0.0.1");

        verify(mfaService).triggerMandatorySetup(targetId);
    }

    // --- deleteUser (HU-05) ---

    @Test
    void deleteUserDebeRechazarQueUnAdministradorSeAutoElimine() {
        UUID adminId = UUID.randomUUID();
        UserIdentity admin = new UserIdentity(adminId, "admin@example.com", "ADMINISTRADOR");

        assertThatThrownBy(() -> service.deleteUser(adminId, admin, "127.0.0.1"))
                .isInstanceOf(SelfModificationException.class);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteUserDebeFallarSiElUsuarioObjetivoNoExiste() {
        UUID targetId = UUID.randomUUID();
        UserIdentity admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", "ADMINISTRADOR");
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUser(targetId, admin, "127.0.0.1"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deleteUserDebeRechazarEliminarUnaCuentaAdministrador() {
        UUID targetId = UUID.randomUUID();
        User otroAdmin = buildUser(targetId, RoleName.ADMINISTRADOR);
        UserIdentity admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", "ADMINISTRADOR");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(otroAdmin));

        assertThatThrownBy(() -> service.deleteUser(targetId, admin, "127.0.0.1"))
                .isInstanceOf(AdminDeletionNotAllowedException.class);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteUserDebeEliminarUnClienteYAuditarElEvento() {
        UUID targetId = UUID.randomUUID();
        User cliente = buildUser(targetId, RoleName.CLIENTE);
        UserIdentity admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", "ADMINISTRADOR");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(cliente));

        service.deleteUser(targetId, admin, "127.0.0.1");

        verify(userRepository).delete(cliente);
        verify(auditService).registerEvent(eq(AuditEventType.ELIMINACION_USUARIO), eq(cliente.getEmail()), eq("SUCCESS"), any(), any());
    }
}
