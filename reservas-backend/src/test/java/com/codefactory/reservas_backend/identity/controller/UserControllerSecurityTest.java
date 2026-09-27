package com.codefactory.reservas_backend.identity.controller;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.identity.application.UserManagementService;
import com.codefactory.reservas_backend.identity.application.UserRegistrationService;
import com.codefactory.reservas_backend.identity.infrastructure.SessionRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import com.codefactory.reservas_backend.identity.infrastructure.security.JwtAuthenticationFilter;
import com.codefactory.reservas_backend.identity.infrastructure.security.JwtTokenProvider;
import com.codefactory.reservas_backend.identity.infrastructure.SecurityConfig;
import com.codefactory.reservas_backend.common.security.RestAccessDeniedHandler;
import com.codefactory.reservas_backend.common.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class UserControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private UserRegistrationService userRegistrationService;
    @MockitoBean
    private UserManagementService userManagementService;
    @MockitoBean
    private IdentityService identityService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private SessionRepository sessionRepository;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private AuditService auditService;

    private static final UUID TARGET_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void administradorAccedeAFuncionalidadAutorizadaCambiarRol() throws Exception {
        when(identityService.getCurrentUser())
                .thenReturn(Optional.of(new UserIdentity(UUID.randomUUID(), "admin@example.com", "ADMINISTRADOR")));

        mockMvc.perform(patch("/api/v1/users/{id}/role", TARGET_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PROVEEDOR\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void clienteEsDenegadoAlIntentarCambiarRolesFuncionalidadNoAutorizada() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/role", TARGET_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void administradorAccedeAFuncionalidadAutorizadaEliminarUsuario() throws Exception {
        when(identityService.getCurrentUser())
                .thenReturn(Optional.of(new UserIdentity(UUID.randomUUID(), "admin@example.com", "ADMINISTRADOR")));

        mockMvc.perform(delete("/api/v1/users/{id}", TARGET_USER_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void clienteEsDenegadoAlIntentarEliminarUsuarioFuncionalidadNoAutorizada() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{id}", TARGET_USER_ID))
                .andExpect(status().isForbidden());
    }
}