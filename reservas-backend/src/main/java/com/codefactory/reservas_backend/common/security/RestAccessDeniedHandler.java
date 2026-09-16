package com.codefactory.reservas_backend.common.security;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.common.error.ApiError;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Responde 403 con el formato uniforme de ApiError (errores-api-sprint-1.md
 * sección 6) cuando authorizeHttpRequests deniega el acceso a nivel de
 * filtro (rol insuficiente en una ruta con requestMatchers). Las denegaciones
 * a nivel de método (@PreAuthorize) las captura GlobalExceptionHandler, que
 * corre dentro del ciclo normal de DispatcherServlet; este handler solo ve
 * las que ocurren antes de llegar ahí — ver GlobalExceptionHandler para el
 * resto de casos de HU-06.
 *
 * Registra el intento en auditoría (interfaces-modulos-sprint-1.md sección
 * 4: "Registrar accesos rechazados cuando sea necesario") cuando hay un
 * usuario autenticado identificable; una petición sin autenticar nunca llega
 * aquí (la resuelve RestAuthenticationEntryPoint con 401, no con 403).
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserIdentity identity) {
            auditService.registerEvent(AuditEventType.ACCESO_DENEGADO, identity.email(), "REJECTED",
                    request.getMethod() + " " + request.getRequestURI(), request.getRemoteAddr());
        }

        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("FORBIDDEN")
                .message("No tiene permisos para realizar esta operación")
                .path(request.getRequestURI())
                .build();

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), error);
    }
}
