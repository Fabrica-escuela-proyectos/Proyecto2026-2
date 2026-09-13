package com.codefactory.reservas_backend.identity.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración base de seguridad — Fase 2 del prompt maestro (rol:
 * Ingeniero de Seguridad de Aplicaciones).
 *
 * Implementa la decisión ya adoptada en
 * ADR-002-autenticacion-y-sesiones.md (Estado: Aceptado): Spring Security +
 * JWT, sesión STATELESS, token con expiración máxima de 1 hora y tabla de
 * sesiones para revocación (ver identity.domain.Session).
 *
 * Alcance real de Simon en Sprint 1: dejar la base lista para que HU02
 * (login, fuera de su tarea individual) conecte el filtro de autenticación
 * JWT sobre esta configuración. JwtTokenProvider
 * (identity.infrastructure.security) ya genera y valida tokens de 1 hora,
 * pero todavía no hay ningún filtro que lo invoque en esta cadena — por eso
 * el único endpoint protegido explícitamente distinto de "todo autenticado"
 * es el de registro (HU-01), que debe seguir siendo público.
 *
 * - CSRF deshabilitado: coherente con una API stateless sin cookies de
 *   sesión (ADR-002 sección 9, alternativa "JWT sin control de revocación"
 *   descartada, pero el transporte sigue siendo header Authorization, no
 *   cookie).
 * - CORS: se deja restringido por defecto (Lineamientos Sec. 3.4); el
 *   detalle de orígenes permitidos depende del dominio del frontend, que
 *   aún no está definido en Sprint 1.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/users").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
