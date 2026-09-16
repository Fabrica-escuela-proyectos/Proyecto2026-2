package com.codefactory.reservas_backend.identity.infrastructure;

import com.codefactory.reservas_backend.common.security.RestAccessDeniedHandler;
import com.codefactory.reservas_backend.common.security.RestAuthenticationEntryPoint;
import com.codefactory.reservas_backend.identity.infrastructure.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
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
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
