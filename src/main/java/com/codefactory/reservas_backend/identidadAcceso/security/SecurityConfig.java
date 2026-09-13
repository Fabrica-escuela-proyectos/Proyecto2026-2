package com.codefactory.reservas_backend.identidadAcceso.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
 
/**
 * Configuracion base de seguridad - Fase 2 del prompt maestro (rol: Ingeniero
 * de Seguridad de Aplicaciones).
 *
 * Alcance real de Simon en Sprint 1: dejar la base lista para que HU02
 * (login, fuera de su tarea individual) construya el flujo completo sobre
 * ella. No se implementa aqui el login.
 *
 * Decisiones tomadas (insumo para ADR-002, ver docs/ADR-002-insumos.md):
 * - Sesion STATELESS: la Matriz tecnica muestra el flujo de HU02 terminando
 *   en "JWT", asi que se prepara la app para autenticacion basada en token,
 *   no en sesion de servidor.
 * - CSRF deshabilitado: coherente con una API stateless sin cookies de
 *   sesion. Si el equipo decide usar cookies HttpOnly/Secure/SameSite para
 *   transportar el token, este punto debe revisarse explicitamente.
 * - CORS: se deja restringido por defecto (Lineamientos Sec. 3.4); el
 *   detalle de origenes permitidos depende del dominio del frontend, que
 *   aun no esta definido en Sprint 1.
 * - El endpoint de registro (HU-01) es el unico publico en este sprint.
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
