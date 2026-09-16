package com.codefactory.reservas_backend.identity.controller;

import com.codefactory.reservas_backend.identity.controller.dto.LoginRequest;
import com.codefactory.reservas_backend.identity.controller.dto.RegisterUserRequest;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.RoleRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración de HU-02 (login), HU-04 (logout) y del mecanismo
 * transversal de HU-06 (control de acceso por rol/pertenencia) contra una
 * base de datos PostgreSQL real en contenedor — mismo enfoque que
 * UserRegistrationIntegrationTest (HU-01).
 *
 * Requiere Docker disponible en la máquina/pipeline donde se ejecute; no se
 * pudo correr dentro de este entorno de generación por no tener acceso a
 * Docker (mismo caso ya documentado en UserRegistrationIntegrationTest).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndAccessControlIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reservas__test")
            .withUsername("reservas__test")
            .withPassword("reservas__test");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "Segura#2026";

    private void registerClient(String email, String cellphone) throws Exception {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setFullName("Usuario de Prueba");
        request.setEmail(email);
        request.setCellphone(cellphone);
        request.setPassword(PASSWORD);

        mockMvc.perform(post("/api/v1/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)));
    }

    private String login(String email) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        return json.get("token").asText();
    }

    /** Crea (o promueve) directamente en BD un usuario ADMINISTRADOR: no hay
     * endpoint de bootstrap para el primer administrador de la plataforma
     * (fuera del alcance de las HU documentadas), así que las pruebas que
     * necesitan un admin arrancan el fixture por repositorio. */
    private String createAdminAndLogin(String email, String cellphone) throws Exception {
        Role adminRole = roleRepository.findByName(RoleName.ADMINISTRADOR).orElseThrow();
        User admin = User.builder()
                .fullName("Administradora de Prueba")
                .email(email)
                .cellphone(cellphone)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .roles(Set.of(adminRole))
                .build();
        userRepository.save(admin);
        return login(email);
    }

    @Test
    void loginExitosoDebeDevolverTokenYRolDelUsuario() throws Exception {
        registerClient("login.exitoso@example.com", "3101111111");

        LoginRequest request = new LoginRequest();
        request.setEmail("login.exitoso@example.com");
        request.setPassword(PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLIENTE"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void loginConCredencialesInvalidasDebeRechazarse() throws Exception {
        registerClient("login.invalido@example.com", "3102222222");

        LoginRequest request = new LoginRequest();
        request.setEmail("login.invalido@example.com");
        request.setPassword("otra-contrasena");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void endpointProtegidoSinTokenDebeRechazarseConFormatoUniforme() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void usuarioAutenticadoDebePoderConsultarSuPropiaInformacion() throws Exception {
        registerClient("propio.perfil@example.com", "3103333333");
        String token = login("propio.perfil@example.com");
        User user = userRepository.findByEmailIgnoreCase("propio.perfil@example.com").orElseThrow();

        mockMvc.perform(get("/api/v1/users/" + user.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("propio.perfil@example.com"));
    }

    @Test
    void clienteNoDebePoderConsultarInformacionDeOtroUsuario() throws Exception {
        registerClient("cliente.a@example.com", "3104444444");
        registerClient("cliente.b@example.com", "3105555555");
        String tokenA = login("cliente.a@example.com");
        User clienteB = userRepository.findByEmailIgnoreCase("cliente.b@example.com").orElseThrow();

        mockMvc.perform(get("/api/v1/users/" + clienteB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void clienteNoDebePoderCambiarRolesAjenos() throws Exception {
        registerClient("cliente.sinrol.a@example.com", "3106666666");
        registerClient("cliente.sinrol.b@example.com", "3107777777");
        String tokenA = login("cliente.sinrol.a@example.com");
        User clienteB = userRepository.findByEmailIgnoreCase("cliente.sinrol.b@example.com").orElseThrow();

        mockMvc.perform(patch("/api/v1/users/" + clienteB.getId() + "/role")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content("{\"role\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorDebePoderCambiarElRolDeUnCliente() throws Exception {
        registerClient("asciende@example.com", "3108888888");
        String tokenAdmin = createAdminAndLogin("admin.cambia.rol@example.com", "3109999999");
        User cliente = userRepository.findByEmailIgnoreCase("asciende@example.com").orElseThrow();

        mockMvc.perform(patch("/api/v1/users/" + cliente.getId() + "/role")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content("{\"role\":\"PROVEEDOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PROVEEDOR"));
    }

    @Test
    void administradorNoDebePoderModificarSuPropioRol() throws Exception {
        String tokenAdmin = createAdminAndLogin("admin.autocambio@example.com", "3110000000");
        User admin = userRepository.findByEmailIgnoreCase("admin.autocambio@example.com").orElseThrow();

        mockMvc.perform(patch("/api/v1/users/" + admin.getId() + "/role")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content("{\"role\":\"CLIENTE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorDebePoderEliminarUnClienteYElClienteEliminadoPierdeElAcceso() throws Exception {
        registerClient("eliminado@example.com", "3115555555");
        String tokenCliente = login("eliminado@example.com");
        String tokenAdmin = createAdminAndLogin("admin.elimina@example.com", "3116666666");
        User cliente = userRepository.findByEmailIgnoreCase("eliminado@example.com").orElseThrow();

        mockMvc.perform(delete("/api/v1/users/" + cliente.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/" + cliente.getId())
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutDebeInvalidarElTokenParaOperacionesProtegidasPosteriores() throws Exception {
        registerClient("cierra.sesion@example.com", "3111111111");
        String token = login("cierra.sesion@example.com");
        User user = userRepository.findByEmailIgnoreCase("cierra.sesion@example.com").orElseThrow();

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/" + user.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registroDeProveedorDebeAsignarRolProveedorYCrearNegocio() throws Exception {
        String payload = """
                {
                  "fullName": "Carlos Gómez",
                  "email": "carlos.integracion@example.com",
                  "cellphone": "3112222222",
                  "password": "%s",
                  "businessName": "Centro Deportivo ABC"
                }
                """.formatted(PASSWORD);

        mockMvc.perform(post("/api/v1/providers")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PROVEEDOR"))
                .andExpect(jsonPath("$.businessName").value("Centro Deportivo ABC"));
    }

    @Test
    void proveedorNoDebePoderConsultarElNegocioDeOtroProveedor() throws Exception {
        String payloadA = """
                {"fullName":"Proveedor A","email":"proveedor.a@example.com","cellphone":"3113333333","password":"%s","businessName":"Negocio A"}
                """.formatted(PASSWORD);
        String payloadB = """
                {"fullName":"Proveedor B","email":"proveedor.b@example.com","cellphone":"3114444444","password":"%s","businessName":"Negocio B"}
                """.formatted(PASSWORD);

        mockMvc.perform(post("/api/v1/providers").contentType("application/json").content(payloadA));
        String bodyB = mockMvc.perform(post("/api/v1/providers").contentType("application/json").content(payloadB))
                .andReturn().getResponse().getContentAsString();
        String providerIdB = objectMapper.readTree(bodyB).get("providerId").asText();

        String tokenA = login("proveedor.a@example.com");

        mockMvc.perform(get("/api/v1/providers/" + providerIdB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }
}
