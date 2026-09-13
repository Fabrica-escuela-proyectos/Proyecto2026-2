package com.codefactory.reservas_backend.identity.controller;

import com.codefactory.reservas_backend.identity.controller.dto.RegisterUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración de HU-01 contra una base de datos PostgreSQL real
 * en contenedor, tal como exige Lineamientos Sec. 3.5 ("automatización:
 * pruebas unitarias, integración con base de datos real o contenedor y
 * aceptación del backend").
 *
 * Las aserciones de error usan el esquema {status, error, message, path,
 * fields} de errores-api-sprint-1.md (antes: errorCode/details/traceId).
 *
 * Requiere Docker disponible en la máquina/pipeline donde se ejecute; no se
 * pudo correr dentro de este entorno de generación por no tener acceso a
 * red/Docker (ver docs/HU-01-checklist.md, sección "Estado de ejecución").
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserRegistrationIntegrationTest {

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

    private RegisterUserRequest validRequest(String email, String cellphone) {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setFullName("Simon Betancur Sosa");
        request.setEmail(email);
        request.setCellphone(cellphone);
        request.setPassword("Segura#2026");
        return request;
    }

    @Test
    void debeCrearUsuarioConDatosValidosYRolCliente() throws Exception {
        // Arrange
        RegisterUserRequest request = validRequest("simon.integracion@example.com", "3009876543");

        // Act / Assert
        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLIENTE"));
    }

    @Test
    void debeRechazarRegistroConCorreoDuplicado() throws Exception {
        // Arrange: primer registro exitoso
        RegisterUserRequest primero = validRequest("duplicado@example.com", "3011112222");
        mockMvc.perform(post("/api/v1/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(primero)));

        // Act: segundo intento con el mismo correo, distinto celular
        RegisterUserRequest segundo = validRequest("duplicado@example.com", "3022223333");

        // Assert
        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(segundo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("El correo electrónico ya está en uso"));
    }

    @Test
    void debeRechazarRegistroConCelularDuplicado() throws Exception {
        // Arrange: primer registro exitoso
        RegisterUserRequest primero = validRequest("celular-dup-1@example.com", "3044445555");
        mockMvc.perform(post("/api/v1/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(primero)));

        // Act: segundo intento con el mismo celular, distinto correo
        RegisterUserRequest segundo = validRequest("celular-dup-2@example.com", "3044445555");

        // Assert
        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(segundo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("El número de celular ya está en uso"));
    }

    @Test
    void debeRechazarRegistroConFormatoDeCorreoInvalido() throws Exception {
        // Arrange
        RegisterUserRequest request = validRequest("usuario@@dominio", "3033334444");

        // Act / Assert
        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.email").exists());
    }

    // --- Escenario "Campo obligatorio faltante" (Scenario Outline de HU-01) ---
    // Antes marcado como pendiente en docs/HU-01-checklist.md; se agrega un
    // caso explícito por campo en este refactor.

    @Test
    void debeRechazarRegistroSinNombreCompleto() throws Exception {
        RegisterUserRequest request = validRequest("sinnombre@example.com", "3055556666");
        request.setFullName(null);

        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.fullName").exists());
    }

    @Test
    void debeRechazarRegistroSinCorreo() throws Exception {
        RegisterUserRequest request = validRequest(null, "3066667777");

        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.email").exists());
    }

    @Test
    void debeRechazarRegistroSinCelular() throws Exception {
        RegisterUserRequest request = validRequest("sincelular@example.com", null);

        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.cellphone").exists());
    }

    @Test
    void debeRechazarRegistroSinContrasena() throws Exception {
        RegisterUserRequest request = validRequest("sinpassword@example.com", "3077778888");
        request.setPassword(null);

        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.password").exists());
    }
}
