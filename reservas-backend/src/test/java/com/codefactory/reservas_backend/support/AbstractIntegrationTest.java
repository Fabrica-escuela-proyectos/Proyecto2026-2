package com.codefactory.reservas_backend.support;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.audit.domain.AuditLog;
import com.codefactory.reservas_backend.audit.infrastructure.AuditLogRepository;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.Session;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.RoleRepository;
import com.codefactory.reservas_backend.identity.infrastructure.SessionRepository;
import com.codefactory.reservas_backend.identity.infrastructure.security.JwtTokenProvider;
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

import java.time.Duration;
import java.time.Instant;
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
    @Autowired
    protected SessionRepository sessionRepository;
    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

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

    // --- Escenarios del Sprint 3: proveedor con negocio, servicio y recurso listos para reservar ---

    protected static final java.time.ZoneId BOGOTA = java.time.ZoneId.of("America/Bogota");

    /** Proveedor con un negocio, un servicio de 60 min y un recurso "Sala 1" asignado, de lunes a viernes 08:00-20:00. */
    protected record ProviderSetup(String email, String token, UUID businessId, UUID serviceId, UUID resourceId) {
    }

    protected record ClientSetup(String email, String token, UUID id) {
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Un lunes a más de una semana: lejos de la antelación mínima y de los bordes de las pruebas. */
    protected static java.time.LocalDate upcomingMonday() {
        return java.time.LocalDate.now(BOGOTA).plusDays(7)
                .with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));
    }

    protected ProviderSetup newProvider() throws Exception {
        String email = uniqueEmail("proveedor");
        String payload = """
                {"fullName":"Proveedor de Prueba","email":"%s","cellphone":"%s","password":"%s","businessName":"Negocio %s"}
                """.formatted(email, uniquePhone(), PASSWORD, UUID.randomUUID().toString().substring(0, 6));
        MvcResult reg = mockMvc.perform(post("/api/v1/providers").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn();
        UUID businessId = UUID.fromString(json(reg).get("businessId").asText());
        String token = login(email);
        UUID serviceId = UUID.fromString(json(mockMvc.perform(post("/api/v1/businesses/" + businessId + "/services")
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Corte\",\"durationMinutes\":60,\"priceCop\":25000}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asText());
        UUID resourceId = newResource(token, businessId, "Sala 1");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/services/" + serviceId + "/resources")
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceIds\":[\"" + resourceId + "\"]}")).andExpect(status().isOk());
        return new ProviderSetup(email, token, businessId, serviceId, resourceId);
    }

    /** Registra un recurso SALA con horario de lunes a viernes 08:00-20:00. */
    protected UUID newResource(String token, UUID businessId, String name) throws Exception {
        UUID resourceId = UUID.fromString(json(mockMvc.perform(post("/api/v1/businesses/" + businessId + "/resources")
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"type\":\"SALA\"}")).andExpect(status().isCreated()).andReturn())
                .get("id").asText());
        String week = "{\"days\":[" + String.join(",", java.util.stream.IntStream.rangeClosed(1, 5).mapToObj(
                d -> "{\"dayOfWeek\":" + d + ",\"ranges\":[{\"start\":\"08:00\",\"end\":\"20:00\"}]}").toList()) + "]}";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/resources/" + resourceId + "/availability")
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(week))
                .andExpect(status().isOk());
        return resourceId;
    }

    protected ClientSetup newClient() throws Exception {
        String email = uniqueEmail("cliente");
        registerClient(email, uniquePhone());
        return new ClientSetup(email, login(email), userRepository.findByEmailIgnoreCase(email).orElseThrow().getId());
    }

    /** Crea una reserva por la API; devuelve el resultado sin exigir el código. */
    protected MvcResult bookResult(ClientSetup client, UUID serviceId, java.time.LocalDate date, String start, String end)
            throws Exception {
        return mockMvc.perform(post("/api/v1/bookings").header("Authorization", bearer(client.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":\"" + serviceId + "\",\"date\":\"" + date + "\",\"startTime\":\"" + start
                        + "\",\"endTime\":\"" + end + "\"}")).andReturn();
    }

    /** Crea una reserva por la API, exige 201 y devuelve su id. */
    protected UUID book(ClientSetup client, UUID serviceId, java.time.LocalDate date, String start, String end) throws Exception {
        MvcResult r = bookResult(client, serviceId, date, start, end);
        org.assertj.core.api.Assertions.assertThat(r.getResponse().getStatus())
                .as("reserva: %s", r.getResponse().getContentAsString()).isEqualTo(201);
        return UUID.fromString(json(r).get("id").asText());
    }

    /** Horas de inicio libres del servicio en una fecha (HU-20, pública). */
    protected List<String> availableStarts(UUID serviceId, java.time.LocalDate date) throws Exception {
        MvcResult r = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/services/" + serviceId + "/availability?date=" + date)).andExpect(status().isOk()).andReturn();
        List<String> starts = new java.util.ArrayList<>();
        json(r).get("slots").forEach(s -> starts.add(s.get("start").asText()));
        return starts;
    }

    /** Eventos de auditoría de un tipo y un sujeto, para comprobar que quedaron persistidos. */
    protected List<AuditLog> auditEvents(AuditEventType type, String subjectEmail) {
        return auditLogRepository.findAll().stream()
                .filter(log -> log.getEventType() == type && subjectEmail.equalsIgnoreCase(log.getSubjectEmail()))
                .toList();
    }

    /**
     * Simula una sesión vencida (más de 1 hora): deja la sesión del token con {@code expires_at} en el pasado.
     * El JWT sigue siendo válido de firma, así que lo que se prueba es que el sistema consulte la sesión.
     */
    protected void expireSession(String token) {
        String tokenId = jwtTokenProvider.parseAndValidate(token).getId();
        Session session = sessionRepository.findByTokenId(tokenId).orElseThrow();
        session.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
        sessionRepository.save(session);
    }
}
