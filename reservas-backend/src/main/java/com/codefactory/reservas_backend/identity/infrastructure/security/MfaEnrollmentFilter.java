package com.codefactory.reservas_backend.identity.infrastructure.security;

import com.codefactory.reservas_backend.common.error.ApiError;
import com.codefactory.reservas_backend.identity.application.MfaService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.identity.domain.MfaEnrollmentRequiredException;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Enrolamiento de MFA obligatorio para administradores (ADR-004, decisión
 * P4; HU-05: "evento obligatorio para la creación de MFA para el nuevo
 * Administrador"). Un ADMINISTRADOR cuya MFA todavía no está activa puede
 * iniciar sesión, pero solo puede usar {@code /api/v1/auth/mfa/**} (para
 * completar el enrolamiento) y el cierre de sesión; todo lo demás responde
 * {@code 403 MFA_ENROLLMENT_REQUIRED}. Incluye al administrador creado por
 * AdminBootstrapRunner y a cualquier usuario ascendido por HU-05.
 *
 * Corre justo después de JwtAuthenticationFilter (ya hay un UserIdentity en
 * el SecurityContext) y antes de la autorización por ruta. Se consulta el
 * estado de MFA en cada petición de un administrador, así que activarla
 * surte efecto de inmediato sin volver a iniciar sesión. Las rutas permitidas
 * se comparan con los mismos {@link RequestMatcher} que usa Spring Security;
 * las secuencias de tipo {@code /../} que intentarían evadirlas ya las rechaza
 * StrictHttpFirewall antes de que la petición llegue a este filtro.
 */
@Component
@RequiredArgsConstructor
public class MfaEnrollmentFilter extends OncePerRequestFilter {

    private static final List<RequestMatcher> ALLOWED_WITHOUT_MFA = List.of(
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/auth/mfa/**"),
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/auth/logout"),
            PathPatternRequestMatcher.withDefaults().matcher("/actuator/health"));

    private final MfaService mfaService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (mustEnrollBefore(request)) {
            writeEnrollmentRequired(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean mustEnrollBefore(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserIdentity identity)) {
            return false;
        }
        if (!RoleName.ADMINISTRADOR.name().equals(identity.role())) {
            return false;
        }
        if (ALLOWED_WITHOUT_MFA.stream().anyMatch(matcher -> matcher.matches(request))) {
            return false;
        }
        return !mfaService.isEnabled(identity.id());
    }

    private void writeEnrollmentRequired(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("MFA_ENROLLMENT_REQUIRED")
                .message(MfaEnrollmentRequiredException.DEFAULT_MESSAGE)
                .path(request.getRequestURI())
                .build();

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), error);
    }
}
