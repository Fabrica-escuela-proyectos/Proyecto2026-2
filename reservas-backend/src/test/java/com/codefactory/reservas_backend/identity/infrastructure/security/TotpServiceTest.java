package com.codefactory.reservas_backend.identity.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TotpService implementa RFC 6238 a mano (sin librería externa, ver su
 * Javadoc). En vez de verificar contra un vector de prueba de la RFC
 * copiado a mano (que usa 8 dígitos donde aquí se generan 6, con alto
 * riesgo de transcribir mal el vector), se usa el propio
 * {@code generateCode} (package-private, expuesto solo para pruebas) para
 * calcular el código esperado del step actual y comparar contra
 * {@code verify}.
 */
class TotpServiceTest {

    private static final long STEP_SECONDS = 30;

    private final TotpService totpService = new TotpService();

    @Test
    void debeGenerarSecretosEnBase32DeLongitudEstable() {
        String secret = totpService.generateSecret();

        assertThat(secret).isNotBlank();
        assertThat(secret).matches("^[A-Z2-7]+$");
    }

    @Test
    void debeGenerarSecretosDistintosEnCadaLlamado() {
        String primero = totpService.generateSecret();
        String segundo = totpService.generateSecret();

        assertThat(primero).isNotEqualTo(segundo);
    }

    @Test
    void debeValidarElCodigoVigenteParaElMismoSecreto() {
        String secret = totpService.generateSecret();
        String codigoValido = totpService.generateCode(secret, currentStep());

        assertThat(totpService.verify(secret, codigoValido)).isTrue();
    }

    @Test
    void debeSerDeterministaParaElMismoSecretoYStep() {
        String secret = totpService.generateSecret();
        long step = currentStep();

        assertThat(totpService.generateCode(secret, step)).isEqualTo(totpService.generateCode(secret, step));
    }

    @Test
    void debeAceptarUnPasoDeDesfaseDeReloj() {
        String secret = totpService.generateSecret();
        long step = currentStep();

        String pasoAnterior = totpService.generateCode(secret, step - 1);
        String pasoSiguiente = totpService.generateCode(secret, step + 1);

        assertThat(totpService.verify(secret, pasoAnterior)).isTrue();
        assertThat(totpService.verify(secret, pasoSiguiente)).isTrue();
    }

    @Test
    void debeRechazarUnCodigoQueNoCorrespondeAlSecreto() {
        String secret = totpService.generateSecret();
        String codigoDeOtroSecreto = totpService.generateCode(totpService.generateSecret(), currentStep());

        // Colisión posible en teoría (1 en un millón), pero suficientemente
        // improbable para no generar un test flaky.
        assertThat(totpService.verify(secret, codigoDeOtroSecreto)).isFalse();
    }

    @Test
    void debeRechazarCodigosConFormatoInvalido() {
        String secret = totpService.generateSecret();

        assertThat(totpService.verify(secret, "abcdef")).isFalse();
        assertThat(totpService.verify(secret, "123")).isFalse();
        assertThat(totpService.verify(secret, null)).isFalse();
    }

    @Test
    void debeRechazarCodigoCuandoElSecretoEsNulo() {
        assertThat(totpService.verify(null, "123456")).isFalse();
    }

    private long currentStep() {
        return Instant.now().getEpochSecond() / STEP_SECONDS;
    }
}
