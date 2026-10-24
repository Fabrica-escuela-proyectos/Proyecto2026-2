package com.codefactory.reservas_backend.support;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.audit.domain.AuditLog;
import com.codefactory.reservas_backend.audit.infrastructure.AuditLogRepository;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.RoleRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de las pruebas de integración: Spring completo + PostgreSQL 16 real en
 * un contenedor (Lineamientos Sec. 3.5), con Flyway aplicando las migraciones
 * de verdad.
 *
 * El contenedor es un singleton de la JVM: arranca una vez y lo comparten
 * todas las clases de integración (y {@code ReservasBackendApplicationTests}),
 * que además comparten el contexto de Spring. Por eso cada prueba debe usar
 * correos y celulares propios (ver {@link #uniqueEmail} y {@link #uniquePhone})
 * y no puede suponer una base vacía. Ya no depende de un PostgreSQL local en
 * {@code localhost:5432}, así que corre igual en CI (GitHub Actions).
 *
 * Requiere Docker. Con Docker Desktop 29 o superior hace falta Testcontainers
 * 1.21.4+ (ver pom.xml).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "Segura#2026";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("reservas__test")
            .withUsername("reservas__test")
            .withPassword("reservas__test");

    // Celulares generados: rango 33xxxxxxxx, aparte de los fijos de las
    // pruebas antiguas (30xxxxxxxx y 31xxxxxxxx), para no chocar entre clases.
    private static final AtomicLong PHONE_SEQUENCE = new AtomicLong(3_300_000_000L);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected RoleRepository roleRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected AuditLogRepository auditLogRepository;

    /** Sesión de un administrador con MFA activa: su token (ya con código) y su secreto TOTP. */
    protected record AdminSession(UUID userId, String email, String token, String secret) {

        /** Código TOTP vigente de este administrador (header X-MFA-Code). */
        public String code() {
            return TotpTestSupport.codeNow(secret);
        }
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    protected static String uniquePhone() {
        return String.valueOf(PHONE_SEQUENCE.incrementAndGet());
    }

    /** Registra un cliente por la API y exige 201. */
    protected void registerClient(String email, String cellphone) throws Exception {
        String payload = """
                {"fullName":"Usuario de Prueba","email":"%s","cellphone":"%s","password":"%s"}
                """.formatted(email, cellphone, PASSWORD);
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
    }

    /** Inicia sesión con la contraseña de pruebas y devuelve el resultado completo. */
    protected MvcResult loginResult(String email, String mfaCode) throws Exception {
        String payload = mfaCode == null
                ? """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD)
                : """
                {"email":"%s","password":"%s","mfaCode":"%s"}
                """.formatted(email, PASSWORD, mfaCode);
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andReturn();
    }

    /** Inicia sesión sin código MFA, exige 200 y devuelve el token. */
    protected String login(String email) throws Exception {
        MvcResult result = loginResult(email, null);
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                .as("login de %s: %s", email, result.getResponse().getContentAsString()).isEqualTo(200);
        return json(result).get("token").asText();
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * Crea directamente en BD un ADMINISTRADOR sin MFA (igual que
     * AdminBootstrapRunner: no hay endpoint público para el primero) y
     * devuelve su id.
     */
    protected UUID createAdminWithoutMfa(String email, String cellphone) {
        Role adminRole = roleRepository.findByName(RoleName.ADMINISTRADOR).orElseThrow();
        User admin = User.builder()
                .fullName("Administradora de Prueba")
                .email(email)
                .cellphone(cellphone)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .roles(Set.of(adminRole))
                .build();
        return userRepository.save(admin).getId();
    }

    /**
     * Crea un administrador y completa su enrolamiento de MFA por la API
     * (login sin código -> setup -> activate -> login con código), como lo
     * haría una persona con su app autenticadora.
     */
    protected AdminSession createEnrolledAdmin(String email) throws Exception {
        UUID adminId = createAdminWithoutMfa(email, uniquePhone());
        String pendingToken = login(email);

        MvcResult setup = mockMvc.perform(post("/api/v1/auth/mfa/setup").header("Authorization", "Bearer " + pendingToken))
                .andExpect(status().isOk())
                .andReturn();
        String secret = TotpTestSupport.secretFromOtpauthUri(json(setup).get("otpauthUri").asText());

        mockMvc.perform(post("/api/v1/auth/mfa/activate")
                        .header("Authorization", "Bearer " + pendingToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + TotpTestSupport.codeNow(secret) + "\"}"))
                .andExpect(status().isNoContent());

        MvcResult withCode = loginResult(email, TotpTestSupport.codeNow(secret));
        org.assertj.core.api.Assertions.assertThat(withCode.getResponse().getStatus())
                .as("login del administrador con código: %s", withCode.getResponse().getContentAsString()).isEqualTo(200);
        return new AdminSession(adminId, email, json(withCode).get("token").asText(), secret);
    }

    /** Eventos de auditoría de un tipo y un sujeto, para comprobar que quedaron persistidos. */
    protected List<AuditLog> auditEvents(AuditEventType type, String subjectEmail) {
        return auditLogRepository.findAll().stream()
                .filter(log -> log.getEventType() == type && subjectEmail.equalsIgnoreCase(log.getSubjectEmail()))
                .toList();
    }
}
