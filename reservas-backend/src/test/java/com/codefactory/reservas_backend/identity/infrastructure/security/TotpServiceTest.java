package com.codefactory.reservas_backend.identity.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TotpService implementa RFC 6238 a mano (sin librería externa, ver su
 * Javadoc). La interoperabilidad con Google/Microsoft Authenticator depende
 * de que el algoritmo coincida con la RFC, así que aquí se verifica contra
 * los vectores de prueba oficiales (Apéndice B de la RFC 6238) y no solo
 * contra sí mismo (TC-MFA-01). Las pruebas de ventana y de formato usan un
 * reloj fijo para ser deterministas (TC-MFA-02..04).
 */
class TotpServiceTest {

    private static final long STEP_SECONDS = 30;

    /** Secreto ASCII "12345678901234567890" de la RFC 6238, codificado en Base32. */
    private static final String RFC_SECRET_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private final TotpService totpService = new TotpService();

    private static TotpService serviceAt(long epochSecond) {
        return new TotpService(Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC));
    }

    // --- TC-MFA-01: vectores oficiales de la RFC 6238 (SHA-1) ---
    // La RFC publica códigos de 8 dígitos; el de 6 dígitos son sus últimos 6.

    @ParameterizedTest(name = "T={0} s -> paso {1} -> {2}")
    @CsvSource({
            "59,          1,         287082",
            "1111111109,  37037036,  081804",
            "1111111111,  37037037,  050471",
            "1234567890,  41152263,  005924",
            "2000000000,  66666666,  279037",
            "20000000000, 666666666, 353130"
    })
    void debeCoincidirConLosVectoresDeLaRfc6238(long epochSecond, long step, String expectedCode) {
        assertThat(epochSecond / STEP_SECONDS).as("el paso del vector").isEqualTo(step);

        assertThat(totpService.generateCode(RFC_SECRET_BASE32, step)).isEqualTo(expectedCode);
        // Y de punta a punta por la API pública: verify() en ese instante.
        assertThat(serviceAt(epochSecond).verify(RFC_SECRET_BASE32, expectedCode)).isTrue();
    }

    // --- TC-MFA-02: ventana de tolerancia de ±1 paso ---

    @Test
    void debeAceptarElPasoAnteriorYElSiguienteYRechazarLosDeDosPasosDeDistancia() {
        long step = 100;
        TotpService service = serviceAt(step * STEP_SECONDS + 7);
        String secret = totpService.generateSecret();

        assertThat(service.verify(secret, totpService.generateCode(secret, step))).as("paso actual").isTrue();
        assertThat(service.verify(secret, totpService.generateCode(secret, step - 1))).as("paso -1").isTrue();
        assertThat(service.verify(secret, totpService.generateCode(secret, step + 1))).as("paso +1").isTrue();
        assertThat(service.verify(secret, totpService.generateCode(secret, step - 2))).as("paso -2").isFalse();
        assertThat(service.verify(secret, totpService.generateCode(secret, step + 2))).as("paso +2").isFalse();
    }

    // --- TC-MFA-03: formato del código ---

    @ParameterizedTest(name = "código inválido: \"{0}\"")
    @ValueSource(strings = {"", "123", "12345", "1234567", "abcdef", "12 456", "12345a", " 123456", "123456 "})
    void debeRechazarCodigosConFormatoInvalido(String code) {
        String secret = totpService.generateSecret();

        assertThat(totpService.verify(secret, code)).isFalse();
    }

    @Test
    void debeRechazarCodigoNulo() {
        assertThat(totpService.verify(totpService.generateSecret(), null)).isFalse();
    }

    @Test
    void debeRechazarCodigoCuandoElSecretoEsNulo() {
        assertThat(totpService.verify(null, "123456")).isFalse();
    }

    // --- TC-MFA-04: el secreto generado ---

    @Test
    void debeGenerarSecretosBase32DeTreintaYDosCaracteres() {
        String secret = totpService.generateSecret();

        assertThat(secret).hasSize(32).matches("^[A-Z2-7]+$");
    }

    @Test
    void debeGenerarSecretosDistintosEnCadaLlamado() {
        assertThat(totpService.generateSecret()).isNotEqualTo(totpService.generateSecret());
    }

    // --- Comportamiento general (con el reloj real) ---

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
    void debeRechazarUnCodigoQueNoCorrespondeAlSecreto() {
        long step = 100;
        TotpService service = serviceAt(step * STEP_SECONDS);
        String secret = totpService.generateSecret();
        String otroSecreto = totpService.generateSecret();
        String codigoDeOtroSecreto = totpService.generateCode(otroSecreto, step);

        // Sin colisión posible solo si los códigos difieren (1 en un millón); se
        // descarta ese caso para que la prueba no sea intermitente.
        if (!codigoDeOtroSecreto.equals(totpService.generateCode(secret, step))
                && !codigoDeOtroSecreto.equals(totpService.generateCode(secret, step - 1))
                && !codigoDeOtroSecreto.equals(totpService.generateCode(secret, step + 1))) {
            assertThat(service.verify(secret, codigoDeOtroSecreto)).isFalse();
        }
    }

    private long currentStep() {
        return Instant.now().getEpochSecond() / STEP_SECONDS;
    }
}
