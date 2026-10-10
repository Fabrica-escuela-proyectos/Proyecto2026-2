package com.codefactory.reservas_backend.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlación de peticiones (API-03, OWASP A09): a cada petición se le asigna un identificador que viaja en el
 * header de respuesta {@code X-Request-Id}, en el campo {@code traceId} de todo {@code ApiError} y en cada línea
 * de registro (MDC). Así un error reportado por Calidad o visto en la demostración se encuentra en los logs.
 *
 * Si el cliente envía su propio {@code X-Request-Id} y tiene forma segura (letras, dígitos y guiones, 8 a 64
 * caracteres) se respeta, para correlacionar con sistemas externos; si no, se descarta y se genera uno. Un valor
 * libre nunca llega al log (evita la inyección de líneas falsas en los registros).
 *
 * Es el primer filtro de la cadena: lo alcanzan también los 401/403 que genera Spring Security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "traceId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String traceId = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
