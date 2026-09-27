package com.codefactory.reservas_backend.identity.infrastructure.security;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.Session;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.SessionRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    private Claims claims;

    private JwtAuthenticationFilter filter;

    private static final String RAW_TOKEN = "un-token-cualquiera";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String TOKEN_ID = "jti-123";

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, sessionRepository, userRepository);
    }

    @AfterEach
    void tearDown() {
        // Evita que el contexto de un test "contamine" al siguiente.
        SecurityContextHolder.clearContext();
    }

    @Test
    void debeContinuarSinAutenticarCuandoNoHayHeaderAuthorization() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void debeContinuarSinAutenticarCuandoElHeaderNoTienePrefijoBearer() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic algo");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void debeContinuarSinAutenticarCuandoElTokenEsInvalido() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + RAW_TOKEN);
        when(jwtTokenProvider.parseAndValidate(RAW_TOKEN)).thenThrow(new JwtException("firma inválida"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(sessionRepository, userRepository);
    }

    @Test
    void debeContinuarSinAutenticarCuandoElSubjectNoEsUnUuidValido() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + RAW_TOKEN);
        when(jwtTokenProvider.parseAndValidate(RAW_TOKEN)).thenReturn(claims);
        when(claims.getSubject()).thenReturn("esto-no-es-un-uuid");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(sessionRepository, userRepository);
    }

    @Test
    void debeContinuarSinAutenticarCuandoNoExisteSesionParaElJti() throws Exception {
        mockTokenValido();
        when(sessionRepository.findByTokenId(TOKEN_ID)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userRepository);
    }

    @Test
    void debeContinuarSinAutenticarCuandoLaSesionEstaRevocadaOExpirada() throws Exception {
        mockTokenValido();
        Session sesionInvalida = mock(Session.class);
        when(sesionInvalida.isValid(any(Instant.class))).thenReturn(false);
        when(sessionRepository.findByTokenId(TOKEN_ID)).thenReturn(Optional.of(sesionInvalida));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userRepository);
    }

    @Test
    void debeContinuarSinAutenticarCuandoElUsuarioNoExiste() throws Exception {
        mockTokenValido();
        mockSesionValida();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void debeContinuarSinAutenticarCuandoElUsuarioEstaDeshabilitado() throws Exception {
        mockTokenValido();
        mockSesionValida();
        User usuarioDeshabilitado = mock(User.class);
        when(usuarioDeshabilitado.isEnabled()).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(usuarioDeshabilitado));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void debeAutenticarCorrectamenteConUnTokenYSesionValidos() throws Exception {
        mockTokenValido();
        mockSesionValida();

        User usuario = mock(User.class);
        when(usuario.isEnabled()).thenReturn(true);
        when(usuario.getId()).thenReturn(USER_ID);
        when(usuario.getEmail()).thenReturn("cliente@example.com");

        Role rolCliente = mock(Role.class);
        when(rolCliente.getName()).thenReturn(RoleName.CLIENTE);
        when(usuario.getRoles()).thenReturn(Set.of(rolCliente));

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(usuario));

        filter.doFilterInternal(request, response, filterChain);

        var authentication = (UsernamePasswordAuthenticationToken)
                SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNotNull();
        UserIdentity principal = (UserIdentity) authentication.getPrincipal();
        assertThat(principal.id()).isEqualTo(USER_ID);
        assertThat(principal.email()).isEqualTo("cliente@example.com");
        assertThat(principal.role()).isEqualTo("CLIENTE");
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_CLIENTE");

        verify(filterChain).doFilter(request, response);
    }

    private void mockTokenValido() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + RAW_TOKEN);
        when(jwtTokenProvider.parseAndValidate(RAW_TOKEN)).thenReturn(claims);
        when(claims.getSubject()).thenReturn(USER_ID.toString());
        when(claims.getId()).thenReturn(TOKEN_ID);
    }

    private void mockSesionValida() {
        Session sesionValida = mock(Session.class);
        when(sesionValida.isValid(any(Instant.class))).thenReturn(true);
        when(sessionRepository.findByTokenId(TOKEN_ID)).thenReturn(Optional.of(sesionValida));
    }
}