package com.codefactory.reservas_backend.identity.controller;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.audit.domain.AuditLog;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import com.codefactory.reservas_backend.support.TotpTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo de MFA (TOTP) contra PostgreSQL real, según la política de
 * ADR-004 y la matriz de pruebas de docs/sprint-2/cierre-pendientes-sprint-1.md
 * (§2.4). Los códigos los calcula {@link TotpTestSupport}, una implementación
 * independiente de RFC 6238 que hace lo mismo que una app autenticadora real.
 *
 * Cubre: TC-MFA-06/07/09 (enrolamiento), 11-15 (login en dos pasos), 16-17
 * (enrolamiento obligatorio, incluido el administrador sembrado como lo hace
 * AdminBootstrapRunner), 18-19 (confirmación en operaciones sensibles), 21
 * (bloqueo por intentos), 22 (sin enumeración de usuarios) y la auditoría.
 * Pendientes de la política y fuera de esta prueba: anti-replay (TC-MFA-05/20),
 * cifrado del secreto (TC-MFA-23), reset por otro administrador (TC-MFA-24/25).
 */
class MfaFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String BEARER = "Bearer ";

    // --- TC-MFA-16 / TC-MFA-17: enrolamiento obligatorio ---

    @Test
    void unAdministradorSinMfaPuedeIniciarSesionPeroSoloEnrolarla() throws Exception {
        String email = uniqueEmail("admin.sinmfa");
        UUID adminId = createAdminWithoutMfa(email, uniquePhone());   // igual que el administrador del bootstrap
        String token = login(email);

        // Todo lo que no sea /auth/mfa/** ni el cierre de sesión está bloqueado hasta enrolarse.
        mockMvc.perform(get("/api/v1/users/" + adminId).header("Authorization", BEARER + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MFA_ENROLLMENT_REQUIRED"))
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(patch("/api/v1/users/" + UUID.randomUUID() + "/role")
                        .header("Authorization", BEARER + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CLIENTE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MFA_ENROLLMENT_REQUIRED"));

        // Enrolamiento: setup (idempotente: devuelve el mismo secreto) ...
        MvcResult setup = mockMvc.perform(post("/api/v1/auth/mfa/setup").header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyEnabled").value(false))
                .andReturn();
        String otpauthUri = json(setup).get("otpauthUri").asText();
        assertThat(otpauthUri).startsWith("otpauth://totp/ReservasPlataforma:" + email)
                .contains("issuer=ReservasPlataforma", "digits=6", "period=30");
        String secret = TotpTestSupport.secretFromOtpauthUri(otpauthUri);
        MvcResult setupAgain = mockMvc.perform(post("/api/v1/auth/mfa/setup").header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(TotpTestSupport.secretFromOtpauthUri(json(setupAgain).get("otpauthUri").asText())).isEqualTo(secret);

        // ... un código incorrecto no activa nada ...
        mockMvc.perform(post("/api/v1/auth/mfa/activate")
                        .header("Authorization", BEARER + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + TotpTestSupport.wrongCode(secret) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/users/" + adminId).header("Authorization", BEARER + token))
                .andExpect(status().isForbidden());

        // ... y uno correcto sí; el efecto es inmediato, sin volver a iniciar sesión.
        mockMvc.perform(post("/api/v1/auth/mfa/activate")
                        .header("Authorization", BEARER + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + TotpTestSupport.codeNow(secret) + "\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/users/" + adminId).header("Authorization", BEARER + token))
                .andExpect(status().isOk());

        // Con la MFA activa, setup ya no vuelve a exponer el secreto (TC-MFA-07).
        MvcResult setupActive = mockMvc.perform(post("/api/v1/auth/mfa/setup").header("Authorization", BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyEnabled").value(true))
                .andReturn();
        assertThat(setupActive.getResponse().getContentAsString()).doesNotContain(secret);
    }

    @Test
    void unAdministradorSinMfaPuedeCerrarSesion() throws Exception {
        String email = uniqueEmail("admin.logout");
        createAdminWithoutMfa(email, uniquePhone());
        String token = login(email);

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", BEARER + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void unAdministradorAscendidoPorOtroQuedaConElEnrolamientoPendienteYObligatorio() throws Exception {
        // HU-05: "evento obligatorio para la creación de MFA para el nuevo Administrador".
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin.asciende"));
        String clientEmail = uniqueEmail("cliente.asciende");
        registerClient(clientEmail, uniquePhone());
        User client = userRepository.findByEmailIgnoreCase(clientEmail).orElseThrow();

        mockMvc.perform(patch("/api/v1/users/" + client.getId() + "/role")
                        .header("Authorization", BEARER + admin.token())
                        .header("X-MFA-Code", admin.code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isOk());

        String newAdminToken = login(clientEmail);
        mockMvc.perform(get("/api/v1/users/" + client.getId()).header("Authorization", BEARER + newAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MFA_ENROLLMENT_REQUIRED"));
        mockMvc.perform(post("/api/v1/auth/mfa/setup").header("Authorization", BEARER + newAdminToken))
                .andExpect(status().isOk());
    }

    // --- TC-MFA-11..15, 22: login en dos pasos ---

    @Test
    void elLoginDeUnAdministradorConMfaActivaSeHaceEnDosPasos() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin.dospasos"));

        // Sin código: 401 MFA_REQUIRED y ningún token.
        MvcResult withoutCode = loginResult(admin.email(), null);
        assertThat(withoutCode.getResponse().getStatus()).isEqualTo(401);
        assertThat(json(withoutCode).get("error").asText()).isEqualTo("MFA_REQUIRED");
        assertThat(json(withoutCode).has("token")).isFalse();

        // Código incorrecto: el mismo 401 genérico de credenciales inválidas.
        MvcResult wrongCode = loginResult(admin.email(), TotpTestSupport.wrongCode(admin.secret()));
        assertThat(wrongCode.getResponse().getStatus()).isEqualTo(401);
        assertThat(json(wrongCode).get("error").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(json(wrongCode).get("message").asText()).isEqualTo("Las credenciales no son válidas");

        // Código correcto: 200 con token y rol.
        MvcResult ok = loginResult(admin.email(), admin.code());
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(ok).get("role").asText()).isEqualTo("ADMINISTRADOR");
        assertThat(json(ok).get("token").asText()).isNotBlank();
    }

    @Test
    void conContrasenaIncorrectaNuncaSeRevelaQueLaCuentaTieneMfa() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin.sinpistas"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Incorrecta#1\",\"mfaCode\":\"%s\"}"
                                .formatted(admin.email(), admin.code())))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(json(result).get("error").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(json(result).get("message").asText()).isEqualTo("Las credenciales no son válidas");
    }

    @Test
    void unClienteQueEnviaCodigoMfaIniciaSesionNormalmente() throws Exception {
        String email = uniqueEmail("cliente.conmfa");
        registerClient(email, uniquePhone());

        MvcResult result = loginResult(email, "123456");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("role").asText()).isEqualTo("CLIENTE");
    }

    // --- TC-MFA-18 / 19: confirmación en operaciones sensibles ---

    @Test
    void cambiarElRolExigeElCodigoMfaDelAdministrador() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin.stepup.rol"));
        String clientEmail = uniqueEmail("cliente.stepup.rol");
        registerClient(clientEmail, uniquePhone());
        User client = userRepository.findByEmailIgnoreCase(clientEmail).orElseThrow();
        String url = "/api/v1/users/" + client.getId() + "/role";
        String body = "{\"role\":\"PROVEEDOR\"}";

        // Sin el header: 401 MFA_REQUIRED.
        mockMvc.perform(patch(url).header("Authorization", BEARER + admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("MFA_REQUIRED"));
        // Con un código incorrecto: 401 genérico.
        mockMvc.perform(patch(url).header("Authorization", BEARER + admin.token())
                        .header("X-MFA-Code", TotpTestSupport.wrongCode(admin.secret()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        assertThat(userRepository.findByEmailIgnoreCase(clientEmail).orElseThrow().getRoles())
                .extracting(role -> role.getName().name()).containsExactly("CLIENTE");

        // Con el código vigente: 200 y el rol cambia.
        mockMvc.perform(patch(url).header("Authorization", BEARER + admin.token())
                        .header("X-MFA-Code", admin.code())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PROVEEDOR"));
    }

    @Test
    void eliminarUnUsuarioExigeElCodigoMfaDelAdministrador() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin.stepup.del"));
        String clientEmail = uniqueEmail("cliente.stepup.del");
        registerClient(clientEmail, uniquePhone());
        User client = userRepository.findByEmailIgnoreCase(clientEmail).orElseThrow();
        String url = "/api/v1/users/" + client.getId();

        mockMvc.perform(delete(url).header("Authorization", BEARER + admin.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("MFA_REQUIRED"));
        mockMvc.perform(delete(url).header("Authorization", BEARER + admin.token())
                        .header("X-MFA-Code", TotpTestSupport.wrongCode(admin.secret())))
                .andExpect(status().isUnauthorized());
        assertThat(userRepository.findByEmailIgnoreCase(clientEmail)).isPresent();

        mockMvc.perform(delete(url).header("Authorization", BEARER + admin.token())
                        .header("X-MFA-Code", admin.code()))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findByEmailIgnoreCase(clientEmail)).isEmpty();
    }

    @Test
    void unClienteSigueRecibiendo403AntesDeQueSePidaCodigoMfa() throws Exception {
        String clientEmail = uniqueEmail("cliente.sin.permiso");
        registerClient(clientEmail, uniquePhone());
        String token = login(clientEmail);

        mockMvc.perform(delete("/api/v1/users/" + UUID.randomUUID()).header("Authorization", BEARER + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // --- TC-MFA-21: fuerza bruta ---

    @Test
    void cincoCodigosIncorrectosSeguidosEnElLoginBloqueanLaCuentaConUn429() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin.fuerzabruta"));

        for (int attempt = 1; attempt <= 5; attempt++) {
            MvcResult result = loginResult(admin.email(), TotpTestSupport.wrongCode(admin.secret()));
            assertThat(result.getResponse().getStatus()).as("intento %d", attempt).isEqualTo(401);
        }

        // Ya bloqueada: ni siquiera el código correcto entra, y no se compara la contraseña.
        MvcResult blocked = loginResult(admin.email(), admin.code());
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(json(blocked).get("error").asText()).isEqualTo("TOO_MANY_REQUESTS");
    }

    @Test
    void elBloqueoPorIntentosNoPermiteSaberSiLaCuentaExiste() throws Exception {
        // TC-MFA-22: mismos mensajes para una cuenta que existe y una que no.
        String inexistente = uniqueEmail("no.existe");
        for (int attempt = 1; attempt <= 5; attempt++) {
            MvcResult result = loginResult(inexistente, null);
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(json(result).get("message").asText()).isEqualTo("Las credenciales no son válidas");
        }

        MvcResult blocked = loginResult(inexistente, null);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(json(blocked).get("error").asText()).isEqualTo("TOO_MANY_REQUESTS");
    }

    @Test
    void cincoCodigosIncorrectosSeguidosEnUnaOperacionSensibleBloqueanElStepUp() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin.stepup.bloqueo"));
        String url = "/api/v1/users/" + UUID.randomUUID();

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(delete(url).header("Authorization", BEARER + admin.token())
                            .header("X-MFA-Code", TotpTestSupport.wrongCode(admin.secret())))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(delete(url).header("Authorization", BEARER + admin.token())
                        .header("X-MFA-Code", admin.code()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"));
    }

    // --- Auditoría (ADR-004 P11): los rechazos quedan registrados, sin códigos ---

    @Test
    void losRechazosDeMfaQuedanAuditadosSinElCodigo() throws Exception {
        AdminSession admin = createEnrolledAdmin(uniqueEmail("admin.auditoria"));
        String wrong = TotpTestSupport.wrongCode(admin.secret());

        loginResult(admin.email(), null);                 // falta el código
        loginResult(admin.email(), wrong);                // código incorrecto
        mockMvc.perform(delete("/api/v1/users/" + UUID.randomUUID())
                .header("Authorization", BEARER + admin.token()));                // step-up sin código
        mockMvc.perform(delete("/api/v1/users/" + UUID.randomUUID())
                .header("Authorization", BEARER + admin.token()).header("X-MFA-Code", admin.code()));   // step-up correcto (404)

        List<AuditLog> logins = auditEvents(AuditEventType.LOGIN, admin.email());
        assertThat(logins).extracting(AuditLog::getOutcome, AuditLog::getDetail)
                .contains(org.assertj.core.groups.Tuple.tuple("REJECTED", "se requiere código MFA"),
                        org.assertj.core.groups.Tuple.tuple("REJECTED", "código MFA inválido"));

        List<AuditLog> sensitive = auditEvents(AuditEventType.OPERACION_SENSIBLE, admin.email());
        assertThat(sensitive).extracting(AuditLog::getOutcome).contains("REJECTED", "SUCCESS");

        List<String> everyDetail = new ArrayList<>();
        logins.forEach(log -> everyDetail.add(String.valueOf(log.getDetail())));
        sensitive.forEach(log -> everyDetail.add(String.valueOf(log.getDetail())));
        assertThat(everyDetail).noneMatch(detail -> detail.contains(wrong) || detail.contains(admin.secret()));
    }

    // --- Concurrencia en el enrolamiento ---

    @Test
    void dosSetupSimultaneosNuncaDan500YDejanUnaSolaConfiguracion() throws Exception {
        String email = uniqueEmail("admin.setup.carrera");
        UUID adminId = createAdminWithoutMfa(email, uniquePhone());
        String token = login(email);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/v1/auth/mfa/setup").header("Authorization", BEARER + token))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertThat(statuses).allMatch(code -> code == 200 || code == 409);
        assertThat(statuses).contains(200);
        // Una sola fila de MFA por usuario (restricción única): setup sigue funcionando.
        mockMvc.perform(post("/api/v1/auth/mfa/setup").header("Authorization", BEARER + token))
                .andExpect(status().isOk());
        assertThat(adminId).isNotNull();
    }
}
