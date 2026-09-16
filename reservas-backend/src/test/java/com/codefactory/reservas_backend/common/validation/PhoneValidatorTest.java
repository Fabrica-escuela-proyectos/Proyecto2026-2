package com.codefactory.reservas_backend.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre el escenario Gherkin "Número de celular con formato inválido"
 * (Scenario Outline) y el celular válido "3001234567" usado como ejemplo
 * de éxito en HU-01.
 */
class PhoneValidatorTest {

    private final PhoneValidator validator = new PhoneValidator();

    @Test
    void debeAceptarCelularConFormatoValido() {
        // Arrange
        String celularValido = "3001234567";

        // Act
        boolean resultado = validator.isValid(celularValido, null);

        // Assert
        assertThat(resultado).isTrue();
    }

    @ParameterizedTest(name = "debeRechazarCelularConFormatoInvalido: {0}")
    @ValueSource(strings = {"abcde12345", "12345", "123"})
    void debeRechazarCelularConFormatoInvalido(String celularInvalido) {
        // Arrange (valor parametrizado, tomado literalmente de HU-01)

        // Act
        boolean resultado = validator.isValid(celularInvalido, null);

        // Assert
        assertThat(resultado).isFalse();
    }

    @Test
    void debeRechazarCelularNulo() {
        assertThat(validator.isValid(null, null)).isFalse();
    }
}
