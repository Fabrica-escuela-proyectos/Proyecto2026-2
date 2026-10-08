package com.codefactory.reservas_backend.identity.controller;

import com.codefactory.reservas_backend.identity.controller.dto.LoginRequest;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

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
 * UserRegistrationIntegrationTest (HU-01). El contenedor, el contexto y los
 * ayudantes (registrar, iniciar sesión, administrador con MFA) vienen de
 * {@link AbstractIntegrationTest}.
 *
 * Las operaciones sensibles de un administrador (cambiar rol, eliminar
 * usuario) exigen su código MFA en el header X-MFA-Code (ADR-004), así que
 * estas pruebas usan un administrador enrolado con MFA; el flujo de MFA en
 * sí se prueba en MfaFlowIntegrationTest.
 */
class AuthAndAccessControlIntegrationTest extends AbstractIntegrationTest {

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
                // 1 hora (ADR-002); se calcula a partir de "ahora", así que puede llegar 3599 por el redondeo.
                .andExpect(jsonPath("$.expiresIn").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(3590),
                        org.hamcrest.Matchers.lessThanOrEqualTo(3600))));
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
        AdminSession admin = createEnrolledAdmin("admin.cambia.rol@example.com");
        User cliente = userRepository.findByEmailIgnoreCase("asciende@example.com").orElseThrow();

        mockMvc.perform(patch("/api/v1/users/" + cliente.getId() + "/role")
                        .header("Authorization", "Bearer " + admin.token())
                        .header("X-MFA-Code", admin.code())
                        .contentType("application/json")
                        .content("{\"role\":\"PROVEEDOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PROVEEDOR"));
    }

    @Test
    void administradorNoDebePoderModificarSuPropioRol() throws Exception {
        AdminSession admin = createEnrolledAdmin("admin.autocambio@example.com");

        mockMvc.perform(patch("/api/v1/users/" + admin.userId() + "/role")
                        .header("Authorization", "Bearer " + admin.token())
                        .header("X-MFA-Code", admin.code())
                        .contentType("application/json")
                        .content("{\"role\":\"CLIENTE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorDebePoderEliminarUnClienteYElClienteEliminadoPierdeElAcceso() throws Exception {
        registerClient("eliminado@example.com", "3115555555");
        String tokenCliente = login("eliminado@example.com");
        AdminSession admin = createEnrolledAdmin("admin.elimina@example.com");
        User cliente = userRepository.findByEmailIgnoreCase("eliminado@example.com").orElseThrow();

        mockMvc.perform(delete("/api/v1/users/" + cliente.getId())
                        .header("Authorization", "Bearer " + admin.token())
                        .header("X-MFA-Code", admin.code()))
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

        mockMvc.perform(post("/api/v1/providers").contentType("application/json").content(payloadA))
                .andExpect(status().isCreated());
        String bodyB = mockMvc.perform(post("/api/v1/providers").contentType("application/json").content(payloadB))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String providerIdB = objectMapper.readTree(bodyB).get("providerId").asText();

        String tokenA = login("proveedor.a@example.com");

        mockMvc.perform(get("/api/v1/providers/" + providerIdB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }
}
