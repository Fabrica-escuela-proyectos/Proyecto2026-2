package com.codefactory.reservas_backend.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SEC-05: fuera de dev y test, el arranque se niega a usar valores de relleno o configuración insuficiente. */
class ProductionSafetyGuardTest {

    private static final String REAL_SECRET = "una-clave-propia-del-entorno-de-al-menos-32-caracteres";

    private static MockEnvironment environmentWith(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    void sinPerfilYConConfiguracionCompletaArranca() {
        assertThatCode(() -> new ProductionSafetyGuard(environmentWith(), REAL_SECRET, "clave-real-de-la-base"))
                .doesNotThrowAnyException();
    }

    @Test
    void conPerfilProdYConfiguracionCompletaArranca() {
        assertThatCode(() -> new ProductionSafetyGuard(environmentWith("prod"), REAL_SECRET, "clave-real-de-la-base"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "el perfil {0} no se valida")
    @ValueSource(strings = {"dev", "test"})
    void losPerfilesDeDesarrolloYPruebasNoSeValidan(String perfil) {
        assertThatCode(() -> new ProductionSafetyGuard(environmentWith(perfil), "", ""))
                .doesNotThrowAnyException();
    }

    @Test
    void sinClaveJwtFalla() {
        assertThatThrownBy(() -> new ProductionSafetyGuard(environmentWith(), "", "clave-real-de-la-base"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @ParameterizedTest(name = "la clave de relleno «{0}» se rechaza")
    @ValueSource(strings = {"dev-only-secret-not-for-production-use-change-me-32b",
            "test-only-secret-not-for-production-use-change-me-32b"})
    void laClaveDeRellenoPublicaFalla(String secret) {
        assertThatThrownBy(() -> new ProductionSafetyGuard(environmentWith("prod"), secret, "clave-real-de-la-base"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("relleno");
    }

    @Test
    void unaClaveCortaFalla() {
        assertThatThrownBy(() -> new ProductionSafetyGuard(environmentWith("prod"), "corta", "clave-real-de-la-base"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void sinContrasenaDeBaseFalla() {
        assertThatThrownBy(() -> new ProductionSafetyGuard(environmentWith("prod"), REAL_SECRET, " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }
}
