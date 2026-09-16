package com.codefactory.reservas_backend.common.security;

import com.codefactory.reservas_backend.common.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Responde 401 con el formato uniforme de ApiError (errores-api-sprint-1.md
 * sección 5) cuando una petición sin autenticar (o con sesión
 * expirada/revocada, ver JwtAuthenticationFilter) llega a un endpoint
 * protegido. Sin este componente, Spring Security respondería con su propia
 * página/cuerpo por defecto, rompiendo el contrato uniforme de error que
 * usa el resto de la API (ver GlobalExceptionHandler).
 *
 * Cubre HU-06, escenario "Bloqueo de acceso con sesión expirada o
 * revocada": "debe denegar el acceso y debe solicitar una nueva
 * autenticación".
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("UNAUTHORIZED")
                .message("Se requiere autenticación para acceder a este recurso")
                .path(request.getRequestURI())
                .build();

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), error);
    }
}
