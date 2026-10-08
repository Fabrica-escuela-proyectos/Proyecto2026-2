package com.codefactory.reservas_backend.identity.controller;

import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regresión del issue de Calidad #8 ("el sexto intento de registro supera el
 * límite configurado y crea la cuenta") de punta a punta. El perfil de
 * pruebas relaja el límite de registro para las demás clases (registran
 * decenas de cuentas desde la misma IP); esta clase lo fija en el valor real
 * de producción (5 intentos). Cada prueba usa su propia IP de origen para que
 * los contadores sean independientes.
 */
@TestPropertySource(properties = "security.rate-limit.registration.max-attempts=5")
class RegistrationRateLimitIntegrationTest extends AbstractIntegrationTest {

    private static RequestPostProcessor fromOrigin(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private String clientPayload(String email) {
        return """
                {"fullName":"Usuario de Prueba","email":"%s","cellphone":"%s","password":"%s"}
                """.formatted(email, uniquePhone(), PASSWORD);
    }

    private String providerPayload(String email) {
        return """
                {"fullName":"Proveedor de Prueba","email":"%s","cellphone":"%s","password":"%s","businessName":"Negocio"}
                """.formatted(email, uniquePhone(), PASSWORD);
    }

    @Test
    void elSextoIntentoDesdeElMismoOrigenResponde429YNoCreaLaCuenta() throws Exception {
        String origin = "203.0.113.10";
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/api/v1/users").with(fromOrigin(origin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(clientPayload(uniqueEmail("limite.cliente"))))
                    .andExpect(status().isCreated());
        }

        String sixthEmail = uniqueEmail("limite.sexto");
        mockMvc.perform(post("/api/v1/users").with(fromOrigin(origin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientPayload(sixthEmail)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"));
        assertThat(userRepository.findByEmailIgnoreCase(sixthEmail)).as("el 6.º intento no crea la cuenta").isEmpty();

        // El bloqueo se mantiene para los intentos siguientes.
        mockMvc.perform(post("/api/v1/users").with(fromOrigin(origin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientPayload(uniqueEmail("limite.septimo"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void otroOrigenNoSeVeAfectadoPorElBloqueo() throws Exception {
        String blockedOrigin = "203.0.113.20";
        for (int attempt = 1; attempt <= 6; attempt++) {
            mockMvc.perform(post("/api/v1/users").with(fromOrigin(blockedOrigin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(clientPayload(uniqueEmail("origen.bloqueado"))));
        }

        mockMvc.perform(post("/api/v1/users").with(fromOrigin("203.0.113.21"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientPayload(uniqueEmail("origen.libre"))))
                .andExpect(status().isCreated());
    }

    @Test
    void clientesYProveedoresComparteElMismoContadorPorOrigen() throws Exception {
        String origin = "203.0.113.30";
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post("/api/v1/users").with(fromOrigin(origin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(clientPayload(uniqueEmail("mixto.cliente"))))
                    .andExpect(status().isCreated());
        }
        for (int attempt = 1; attempt <= 2; attempt++) {
            mockMvc.perform(post("/api/v1/providers").with(fromOrigin(origin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(providerPayload(uniqueEmail("mixto.proveedor"))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/v1/providers").with(fromOrigin(origin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(providerPayload(uniqueEmail("mixto.sexto"))))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(post("/api/v1/users").with(fromOrigin(origin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientPayload(uniqueEmail("mixto.septimo"))))
                .andExpect(status().isTooManyRequests());
    }
}
