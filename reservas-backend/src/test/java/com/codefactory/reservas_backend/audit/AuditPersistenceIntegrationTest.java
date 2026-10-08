package com.codefactory.reservas_backend.audit;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.audit.domain.AuditLog;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los intentos rechazados deben quedar en {@code audit_logs} aunque la
 * operación termine en error: Lineamientos §5.3 / HU-02 ("registrar intentos
 * fallidos") y la política de MFA (ADR-004 P11). Los servicios auditan el
 * rechazo y luego lanzan la excepción; si la transacción se revierte por esa
 * excepción, el evento recién auditado se pierde con ella. Estas pruebas
 * comprueban contra PostgreSQL real que no pasa.
 */
class AuditPersistenceIntegrationTest extends AbstractIntegrationTest {

    private String clientPayload(String email, String cellphone) {
        return """
                {"fullName":"Usuario de Prueba","email":"%s","cellphone":"%s","password":"%s"}
                """.formatted(email, cellphone, PASSWORD);
    }

    private String providerPayload(String email, String cellphone) {
        return """
                {"fullName":"Proveedor de Prueba","email":"%s","cellphone":"%s","password":"%s","businessName":"Negocio de Prueba"}
                """.formatted(email, cellphone, PASSWORD);
    }

    @Test
    void unLoginFallidoQuedaAuditadoComoRechazado() throws Exception {
        String email = uniqueEmail("audit.login");
        registerClient(email, uniquePhone());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Incorrecta#1\"}".formatted(email)))
                .andExpect(status().isUnauthorized());

        List<AuditLog> events = auditEvents(AuditEventType.LOGIN, email);
        assertThat(events).extracting(AuditLog::getOutcome).containsExactly("REJECTED");
    }

    @Test
    void unRegistroDeClienteRechazadoPorCorreoDuplicadoQuedaAuditado() throws Exception {
        String email = uniqueEmail("audit.registro");
        registerClient(email, uniquePhone());

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(clientPayload(email, uniquePhone())))
                .andExpect(status().isConflict());

        assertThat(auditEvents(AuditEventType.REGISTRO_USUARIO, email))
                .extracting(AuditLog::getOutcome).containsExactlyInAnyOrder("SUCCESS", "REJECTED");
    }

    @Test
    void unRegistroDeProveedorRechazadoPorCorreoDuplicadoQuedaAuditado() throws Exception {
        String email = uniqueEmail("audit.proveedor");
        mockMvc.perform(post("/api/v1/providers").contentType(MediaType.APPLICATION_JSON)
                        .content(providerPayload(email, uniquePhone())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/providers").contentType(MediaType.APPLICATION_JSON)
                        .content(providerPayload(email, uniquePhone())))
                .andExpect(status().isConflict());

        assertThat(auditEvents(AuditEventType.REGISTRO_PROVEEDOR, email))
                .extracting(AuditLog::getOutcome).containsExactlyInAnyOrder("SUCCESS", "REJECTED");
    }

    @Test
    void unCambioDeRolRechazadoQuedaAuditado() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("audit.rol"));

        mockMvc.perform(patch("/api/v1/users/" + admin.userId() + "/role")
                        .header("Authorization", "Bearer " + admin.token())
                        .header("X-MFA-Code", admin.code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CLIENTE\"}"))
                .andExpect(status().isForbidden());

        assertThat(auditEvents(AuditEventType.CAMBIO_ROL, admin.email()))
                .extracting(AuditLog::getOutcome).containsExactly("REJECTED");
    }
}
