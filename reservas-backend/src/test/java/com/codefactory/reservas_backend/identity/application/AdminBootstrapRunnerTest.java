package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    private static final String PASSWORD = "Segura#2026";

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProvisioningService provisioningService;
    @Mock
    private AuditService auditService;

    private AdminBootstrapRunner runner(String email, String password, String cellphone, String fullName) {
        return new AdminBootstrapRunner(userRepository, provisioningService, auditService,
                email, password, cellphone, fullName);
    }

    @Test
    void noDebeHacerNadaSinVariablesDefinidas() {
        runner("", "", "", "").run(null);

        verifyNoInteractions(userRepository, provisioningService, auditService);
    }

    @Test
    void noDebeCrearNadaSiYaExisteUnAdministrador() {
        when(userRepository.existsByRoles_Name(RoleName.ADMINISTRADOR)).thenReturn(true);

        runner("admin@example.com", PASSWORD, "3001234567", "").run(null);

        verify(provisioningService, never()).provisionUser(any(), any(), any(), any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    void debeCrearElAdministradorInicialYAuditarlo() {
        when(userRepository.existsByRoles_Name(RoleName.ADMINISTRADOR)).thenReturn(false);

        runner("admin@example.com", PASSWORD, "3001234567", "Ana Admin").run(null);

        verify(provisioningService).provisionUser(
                "Ana Admin", "admin@example.com", "3001234567", PASSWORD, RoleName.ADMINISTRADOR);
        verify(auditService).registerEvent(
                AuditEventType.OPERACION_SENSIBLE, "admin@example.com", "SUCCESS",
                "Administrador inicial creado por bootstrap", "bootstrap");
    }

    @Test
    void debeUsarUnNombreporDefectoSiNoSeDefine() {
        when(userRepository.existsByRoles_Name(RoleName.ADMINISTRADOR)).thenReturn(false);

        runner("admin@example.com", PASSWORD, "3001234567", " ").run(null);

        verify(provisioningService).provisionUser(
                "Administrador inicial", "admin@example.com", "3001234567", PASSWORD, RoleName.ADMINISTRADOR);
    }

    @Test
    void debeFallarSiLaConfiguracionEstaIncompleta() {
        when(userRepository.existsByRoles_Name(RoleName.ADMINISTRADOR)).thenReturn(false);

        assertThatThrownBy(() -> runner("admin@example.com", "", "", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD")
                .hasMessageContaining("BOOTSTRAP_ADMIN_CELLPHONE");
        verify(provisioningService, never()).provisionUser(any(), any(), any(), any(), any());
    }

    @Test
    void debeRechazarUnaContrasenaDebilSinFiltrarlaEnElMensaje() {
        when(userRepository.existsByRoles_Name(RoleName.ADMINISTRADOR)).thenReturn(false);

        assertThatThrownBy(() -> runner("admin@example.com", "debil", "3001234567", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("debil"));
        verify(provisioningService, never()).provisionUser(any(), any(), any(), any(), any());
    }

    @Test
    void debeRechazarCelularYCorreoInvalidos() {
        when(userRepository.existsByRoles_Name(RoleName.ADMINISTRADOR)).thenReturn(false);

        assertThatThrownBy(() -> runner("admin@example.com", PASSWORD, "123", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_CELLPHONE");
        assertThatThrownBy(() -> runner("no-es-un-correo", PASSWORD, "3001234567", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_EMAIL");
        verify(provisioningService, never()).provisionUser(any(), any(), any(), any(), any());
    }
}
