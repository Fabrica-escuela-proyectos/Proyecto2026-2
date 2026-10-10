package com.codefactory.reservas_backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Falla al arrancar si una instancia que no es de desarrollo ni de pruebas usa valores de relleno o configuración
 * insuficiente (SEC-05, OWASP A05): la clave JWT de {@code application-dev.yml} es pública (el repositorio lo es),
 * así que cualquiera podría firmar tokens contra un despliegue que la use.
 *
 * Solo se aplica fuera de los perfiles {@code dev} y {@code test}. Un despliegue sin perfil (que antes caía a
 * {@code dev} en silencio) arranca con la configuración base y llega aquí.
 */
@Component
public class ProductionSafetyGuard {

    static final String DEV_PLACEHOLDER_PREFIX = "dev-only-";
    static final String TEST_PLACEHOLDER_PREFIX = "test-only-";
    static final int MIN_SECRET_BYTES = 32;

    public ProductionSafetyGuard(Environment environment,
                                 @Value("${security.jwt.secret:}") String jwtSecret,
                                 @Value("${spring.datasource.password:}") String dbPassword) {
        if (environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            return;
        }
        if (jwtSecret.isBlank()) {
            throw new IllegalStateException("Falta JWT_SECRET: fuera de los perfiles dev y test es obligatorio "
                    + "(mínimo 32 caracteres; ver docs/guia-despliegue-render.md)");
        }
        if (jwtSecret.startsWith(DEV_PLACEHOLDER_PREFIX) || jwtSecret.startsWith(TEST_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException("JWT_SECRET es el valor de relleno de desarrollo, que es público: "
                    + "defina uno propio para este entorno");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
        if (dbPassword.isBlank()) {
            throw new IllegalStateException("Falta DB_PASSWORD: fuera de los perfiles dev y test es obligatoria");
        }
    }
}
