package com.codefactory.reservas_backend.identity.infrastructure.security;

import com.codefactory.reservas_backend.identity.application.MfaService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Enrolamiento de MFA obligatorio para administradores (ADR-004 P4;
 * TC-MFA-16 y TC-MFA-17 a nivel de filtro).
 */
@ExtendWith(MockitoExtension.class)
class MfaEnrollmentFilterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private MfaService mfaService;
    @Mock
    private FilterChain filterChain;

    private MfaEnrollmentFilter filter;
    private UserIdentity admin;

    @BeforeEach
    void setUp() {
        filter = new MfaEnrollmentFilter(mfaService, objectMapper);
        admin = new UserIdentity(UUID.randomUUID(), "admin@example.com", "ADMINISTRADOR");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UserIdentity identity) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                identity, null, AuthorityUtils.createAuthorityList("ROLE_" + identity.role())));
    }

    private MockHttpServletResponse run(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);
        return response;
    }

    @Test
    void debeBloquearConMfaEnrollmentRequiredAUnAdministradorSinMfaActiva() throws Exception {
        authenticateAs(admin);
        when(mfaService.isEnabled(admin.id())).thenReturn(false);

        MockHttpServletResponse response = run("GET", "/api/v1/users/" + UUID.randomUUID());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("error").asText()).isEqualTo("MFA_ENROLLMENT_REQUIRED");
        assertThat(body.get("message").asText()).contains("/api/v1/auth/mfa/setup");
        assertThat(body.get("path").asText()).startsWith("/api/v1/users/");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @ParameterizedTest(name = "un administrador sin MFA puede usar {0}")
    @ValueSource(strings = {"/api/v1/auth/mfa/setup", "/api/v1/auth/mfa/activate", "/api/v1/auth/logout", "/actuator/health"})
    void debeDejarPasarLasRutasDeEnrolamientoYElCierreDeSesion(String uri) throws Exception {
        authenticateAs(admin);

        MockHttpServletResponse response = run("POST", uri);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(any(), any());
        verify(mfaService, never()).isEnabled(any());
    }

    @Test
    void debeDejarPasarAUnAdministradorConMfaActiva() throws Exception {
        authenticateAs(admin);
        when(mfaService.isEnabled(admin.id())).thenReturn(true);

        run("PATCH", "/api/v1/users/" + UUID.randomUUID() + "/role");

        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void elEnrolamientoSurteEfectoDeInmediatoSinVolverAIniciarSesion() throws Exception {
        authenticateAs(admin);
        when(mfaService.isEnabled(admin.id())).thenReturn(false, true);

        assertThat(run("GET", "/api/v1/users/x").getStatus()).isEqualTo(403);
        assertThat(run("GET", "/api/v1/users/x").getStatus()).isEqualTo(200);
    }

    @Test
    void noDebeAfectarANiCuestionarAUnClienteNiConsultarSuMfa() throws Exception {
        authenticateAs(new UserIdentity(UUID.randomUUID(), "cliente@example.com", "CLIENTE"));

        MockHttpServletResponse response = run("GET", "/api/v1/users/x");

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(any(), any());
        verify(mfaService, never()).isEnabled(any());
    }

    @Test
    void noDebeAfectarAUnProveedor() throws Exception {
        authenticateAs(new UserIdentity(UUID.randomUUID(), "proveedor@example.com", "PROVEEDOR"));

        run("GET", "/api/v1/providers/me");

        verify(filterChain).doFilter(any(), any());
        verify(mfaService, never()).isEnabled(any());
    }

    @Test
    void debeDejarPasarLasPeticionesSinAutenticar() throws Exception {
        run("POST", "/api/v1/auth/login");

        verify(filterChain).doFilter(any(), any());
        verify(mfaService, never()).isEnabled(any());
    }

    @Test
    void debeDejarPasarUnPrincipalQueNoEsUnaIdentidadDeLaAplicacion() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "otro-principal", null, AuthorityUtils.createAuthorityList("ROLE_ADMINISTRADOR")));

        run("GET", "/api/v1/users/x");

        verify(filterChain).doFilter(any(), any());
        verify(mfaService, never()).isEnabled(any());
    }
}
