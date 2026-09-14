package com.codefactory.reservas_backend.identity.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt: hash irreversible (a diferencia de un cifrado simétrico, no existe
 * operación inversa, solo "matches" para verificar). Cumple el escenario
 * Gherkin "La información del usuario queda protegida en todo momento" y
 * ADR-002-autenticacion-y-sesiones.md sección 13 ("las contraseñas deberán
 * almacenarse mediante un algoritmo de hash seguro").
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
