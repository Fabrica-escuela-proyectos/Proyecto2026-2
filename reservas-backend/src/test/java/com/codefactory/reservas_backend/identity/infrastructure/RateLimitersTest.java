package com.codefactory.reservas_backend.identity.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de los dos limitadores concretos con su configuración por defecto
 * (la que usa producción): registro (5 intentos por origen) y autenticación
 * (5 fallos por clave).
 */
class RateLimitersTest {

    // --- RegistrationRateLimiter (issue #8) ---

    @Test
    void elRegistroPermiteCincoIntentosPorOrigenYRechazaElSexto() {
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(5, 10, 15);

        for (int intento = 1; intento <= 5; intento++) {
            assertThat(limiter.tryAcquire("203.0.113.7")).as("intento %d", intento).isTrue();
        }

        assertThat(limiter.tryAcquire("203.0.113.7")).as("intento 6").isFalse();
        assertThat(limiter.tryAcquire("203.0.113.8")).as("otro origen").isTrue();
    }

    @Test
    void elRegistroRespetaElMaximoConfigurado() {
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(2, 10, 15);

        assertThat(limiter.tryAcquire("203.0.113.7")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.7")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.7")).isFalse();
    }

    // --- AuthAttemptLimiter (login, MFA y step-up) ---

    @Test
    void laClaveDeLoginNormalizaElCorreoYNoDistingueMayusculasNiEspacios() {
        assertThat(AuthAttemptLimiter.loginKey("1.2.3.4", "  Admin@Example.COM "))
                .isEqualTo(AuthAttemptLimiter.loginKey("1.2.3.4", "admin@example.com"));
    }

    @Test
    void laClaveDeLoginDependeDeLaIpYDelCorreo() {
        assertThat(AuthAttemptLimiter.loginKey("1.2.3.4", "a@example.com"))
                .isNotEqualTo(AuthAttemptLimiter.loginKey("1.2.3.5", "a@example.com"))
                .isNotEqualTo(AuthAttemptLimiter.loginKey("1.2.3.4", "b@example.com"));
    }

    @Test
    void laClaveDeLoginAceptaUnCorreoNulo() {
        assertThat(AuthAttemptLimiter.loginKey("1.2.3.4", null)).isEqualTo("login|1.2.3.4|");
    }

    @Test
    void laAutenticacionBloqueaAlQuintoFalloYUnExitoLoLibera() {
        AuthAttemptLimiter limiter = new AuthAttemptLimiter(5, 15, 15);
        String clave = AuthAttemptLimiter.loginKey("1.2.3.4", "admin@example.com");

        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(clave);
        }
        assertThat(limiter.isBlocked(clave)).isFalse();

        limiter.recordFailure(clave);
        assertThat(limiter.isBlocked(clave)).isTrue();

        limiter.reset(clave);
        assertThat(limiter.isBlocked(clave)).isFalse();
    }

    @Test
    void laClaveDeStepUpEsPorAdministradorYNoSeMezclaConLaDeLogin() {
        UUID admin = UUID.randomUUID();

        assertThat(AuthAttemptLimiter.stepUpKey(admin)).isEqualTo("stepup|" + admin);
        assertThat(AuthAttemptLimiter.stepUpKey(admin))
                .isNotEqualTo(AuthAttemptLimiter.stepUpKey(UUID.randomUUID()))
                .isNotEqualTo(AuthAttemptLimiter.loginKey("1.2.3.4", admin.toString()));
    }
}
