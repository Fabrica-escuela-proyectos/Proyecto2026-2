package com.codefactory.reservas_backend.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre el escenario Gherkin "Contraseña que no cumple la política de
 * seguridad" (Scenario Outline), usando los mismos caracteres especiales de
 * ejemplo listados en HU-01: ¡, *, +, °.
 */
class PasswordValidatorTest {

    private final PasswordValidator validator = new PasswordValidator();

    @Test
    void debeAceptarContrasenaQueCumplePolitica() {
        // Arrange
        String contrasenaValida = "Segura#2026";

        // Act
        boolean resultado = validator.isValid(contrasenaValida, null);

        // Assert
        assertThat(resultado).isTrue();
    }

    @ParameterizedTest(name = "debeRechazarContrasenaQueNoCumplePolitica sin mayúscula, con carácter: {0}")
    @ValueSource(strings = {"¡", "*", "+", "°"})
    void debeRechazarContrasenaSinMayuscula(String caracterEspecial) {
        // Arrange: tiene minúscula, longitud y carácter especial, pero le
        // falta la mayúscula -> debe rechazarse igual.
        String contrasenaDebil = "abcdefg" + caracterEspecial;

        // Act
        boolean resultado = validator.isValid(contrasenaDebil, null);

        // Assert
        assertThat(resultado).isFalse();
    }

    @Test
    void debeRechazarContrasenaMenorA8Caracteres() {
        assertThat(validator.isValid("Ab1#", null)).isFalse();
    }

    @Test
    void debeRechazarContrasenaSinCaracterEspecial() {
        assertThat(validator.isValid("Abcdefgh1", null)).isFalse();
    }

    @Test
    void debeRechazarContrasenaNula() {
        assertThat(validator.isValid(null, null)).isFalse();
    }
}
