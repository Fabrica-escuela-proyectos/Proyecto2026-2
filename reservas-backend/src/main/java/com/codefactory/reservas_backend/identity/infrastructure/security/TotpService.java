package com.codefactory.reservas_backend.identity.infrastructure.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/**
 * TOTP (RFC 6238) implementado solo con lo que trae el JDK — sin librería
 * externa — para no agregar una dependencia nueva únicamente por el MFA de
 * cuentas administrativas (ADR-002 sección 8, HU-02 y HU-05).
 *
 * Parámetros estándar compatibles con cualquier app autenticadora (Google
 * Authenticator, Authy, etc.): HMAC-SHA1, 6 dígitos, paso de 30 segundos.
 * Se acepta una ventana de ±1 paso para tolerar el desfase de reloj típico
 * entre el teléfono del usuario y el servidor.
 */
@Component
public class TotpService {

    private static final int SECRET_BYTES = 20;
    private static final int CODE_DIGITS = 6;
    private static final long STEP_SECONDS = 30;
    private static final int CLOCK_DRIFT_STEPS = 1;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public String generateSecret() {
        byte[] randomBytes = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(randomBytes);
        return base32Encode(randomBytes);
    }

    public boolean verify(String base32Secret, String code) {
        if (base32Secret == null || code == null || !code.matches("\\d{" + CODE_DIGITS + "}")) {
            return false;
        }
        long currentStep = Instant.now().getEpochSecond() / STEP_SECONDS;
        for (long step = currentStep - CLOCK_DRIFT_STEPS; step <= currentStep + CLOCK_DRIFT_STEPS; step++) {
            if (generateCode(base32Secret, step).equals(code)) {
                return true;
            }
        }
        return false;
    }

    // Sin modificador de acceso (package-private) a propósito: permite que
    // TotpServiceTest calcule directamente el código esperado para un
    // step conocido, en vez de tener que fuerza-bruta-buscarlo.
    String generateCode(String base32Secret, long step) {
        byte[] key = base32Decode(base32Secret);
        byte[] counter = ByteBuffer.allocate(8).putLong(step).array();
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counter);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);

            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el código TOTP", e);
        }
    }

    private String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                result.append(BASE32_ALPHABET.charAt(index));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(BASE32_ALPHABET.charAt(index));
        }
        return result.toString();
    }

    private byte[] base32Decode(String base32) {
        String cleaned = base32.trim().toUpperCase(Locale.ROOT).replace("=", "");
        int outputLength = cleaned.length() * 5 / 8;
        byte[] result = new byte[outputLength];
        int buffer = 0;
        int bitsLeft = 0;
        int outputIndex = 0;
        for (char c : cleaned.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                continue;
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[outputIndex++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return result;
    }
}
