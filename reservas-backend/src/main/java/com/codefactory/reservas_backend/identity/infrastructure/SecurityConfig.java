package com.codefactory.reservas_backend.identity.infrastructure;

import com.codefactory.reservas_backend.common.security.RestAccessDeniedHandler;
import com.codefactory.reservas_backend.common.security.RestAuthenticationEntryPoint;
import com.codefactory.reservas_backend.identity.infrastructure.security.JwtAuthenticationFilter;
import com.codefactory.reservas_backend.identity.infrastructure.security.MfaEnrollmentFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

/**
 * Configuración de seguridad — HU02/HU04/HU05/HU06.
 *
 * Implementa la decisión de ADR-002-autenticacion-y-sesiones.md: Spring
 * Security + JWT, sesión STATELESS, token con expiración máxima de 1 hora y
 * tabla de sesiones para revocación.
 *
 * A partir de HU02, JwtAuthenticationFilter puebla el SecurityContext con un
 * UserIdentity (ver IdentityServiceImpl, que ya sabía leerlo) y sus
 * authorities ROLE_&lt;rol&gt;, habilitando tanto las reglas por ruta de
 * authorizeHttpRequests como las anotaciones @PreAuthorize a nivel de método
 * (@EnableMethodSecurity) que usan UserController (HU05) y
 * ProviderController (HU06).
 *
 * RestAuthenticationEntryPoint/RestAccessDeniedHandler reemplazan las
 * respuestas por defecto de Spring Security por el formato uniforme de
 * ApiError (401/403 — errores-api-sprint-1.md secciones 5 y 6), consistente
 * con GlobalExceptionHandler para el resto de códigos.
 *
 * - CSRF deshabilitado: coherente con una API stateless sin cookies de
 *   sesión (ADR-002 sección 9).
 * - CORS: se deja restringido por defecto (Lineamientos Sec. 3.4); el
 *   detalle de orígenes permitidos depende del dominio del frontend, que
 *   aún no está definido en Sprint 1.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final MfaEnrollmentFilter mfaEnrollmentFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Value("${security.cors.allowed-origins:}")
    private List<String> corsAllowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // OWASP-02 (A05): además de las cabeceras por defecto de Spring Security (nosniff, X-Frame-Options,
            // Cache-Control, HSTS en HTTPS) se añaden estas. La CSP «default-src 'none'» es adecuada para una API
            // JSON, pero Swagger UI necesita scripts y estilos propios: se omite en sus rutas.
            .headers(headers -> headers
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "geolocation=(), camera=(), microphone=()"))
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                    request -> !request.getRequestURI().startsWith("/swagger-ui"),
                    new StaticHeadersWriter("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(restAuthenticationEntryPoint)
                .accessDeniedHandler(restAccessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos de registro/login (HU01, HU02, HU03).
                // Se restringe explícitamente al método POST: las demás
                // rutas de /api/v1/users/** (GET/PATCH/DELETE, HU05/HU06)
                // deben exigir autenticación.
                .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/providers").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                // HU-20: consultar horarios libres de un servicio es público (escenario "usuario sin
                // sesión iniciada" del criterio de aceptación). Solo GET y solo esta ruta; el resto de
                // /api/v1/services/** (asignación de recursos, HU-18) sigue exigiendo sesión y rol.
                .requestMatchers(HttpMethod.GET, "/api/v1/services/*/availability").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // ARQ-01: documentación OpenAPI. Las rutas se permiten aquí, pero solo existen si
                // springdoc está habilitado (SWAGGER_ENABLED; apagado por defecto fuera de dev/test),
                // si no responden 404.
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // ADR-004 (P4): un administrador sin MFA activa solo puede enrolarla o cerrar sesión.
            .addFilterAfter(mfaEnrollmentFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * CORS explícito (OWASP-02): solo los orígenes de {@code security.cors.allowed-origins} (variable
     * CORS_ALLOWED_ORIGINS). Vacío = ninguno, y entonces la petición sigue su curso sin cabeceras
     * {@code Access-Control-*}: el navegador no deja leer la respuesta a otro origen.
     */
    private CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = corsAllowedOrigins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
        return request -> {
            if (origins.isEmpty()) {
                return null;
            }
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(origins);
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-MFA-Code", "X-Request-Id"));
            config.setExposedHeaders(List.of("X-Request-Id"));
            config.setAllowCredentials(false);   // el JWT viaja en la cabecera Authorization, no en cookies
            config.setMaxAge(3600L);
            return config;
        };
    }

    /**
     * La autenticación es propia (JWT + tabla de sesiones), no por usuario/contraseña de Spring. Declarar este
     * servicio vacío evita que Spring Boot cree su usuario en memoria con una contraseña generada, que se
     * imprimía en el log en cada arranque (SEC-03).
     */
    @Bean
    public UserDetailsService noUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("La autenticación se hace con JWT");
        };
    }
}
