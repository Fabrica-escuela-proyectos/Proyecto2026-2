package com.codefactory.reservas_backend.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generador de códigos TOTP (RFC 6238) para las pruebas de integración.
 * Es una implementación independiente de TotpService a propósito: si la de
 * producción se desviara del estándar, el código que calcula una app
 * autenticadora real (aquí, esta clase) dejaría de ser aceptado y la prueba
 * fallaría. Para quien prueba a mano, hace lo mismo que Google/Microsoft
 * Authenticator: toma el secreto Base32 de la URI {@code otpauth://} y
 * devuelve el código de 6 dígitos del paso de 30 segundos actual.
 */
public final class TotpTestSupport {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final Pattern SECRET_IN_URI = Pattern.compile("[?&]secret=([A-Za-z2-7]+)");
    private static final long STEP_SECONDS = 30;

    private TotpTestSupport() {
    }

    /** Código vigente ahora mismo para el secreto Base32. */
    public static String codeNow(String base32Secret) {
        return codeAtStep(base32Secret, Instant.now().getEpochSecond() / STEP_SECONDS);
    }

    /** Código de un paso concreto (30 s) para el secreto Base32. */
    public static String codeAtStep(String base32Secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(base32Secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Un código que NO es válido en la ventana actual (±1 paso) para el secreto dado. */
    public static String wrongCode(String base32Secret) {
        long step = Instant.now().getEpochSecond() / STEP_SECONDS;
        String candidate = "000000";
        for (int n = 0; n < 1000; n++) {
            candidate = String.format(Locale.ROOT, "%06d", n * 997 % 1_000_000);
            boolean valid = false;
            for (long s = step - 2; s <= step + 2; s++) {
                valid |= candidate.equals(codeAtStep(base32Secret, s));
            }
            if (!valid) {
                return candidate;
            }
        }
        return candidate;
    }

    /** Extrae el secreto Base32 de una URI {@code otpauth://totp/...?secret=XXXX&...}. */
    public static String secretFromOtpauthUri(String otpauthUri) {
        Matcher matcher = SECRET_IN_URI.matcher(otpauthUri);
        if (!matcher.find()) {
            throw new IllegalArgumentException("La URI otpauth no contiene un secreto");
        }
        return matcher.group(1);
    }

    private static byte[] decodeBase32(String input) {
        String clean = input.trim().toUpperCase(Locale.ROOT).replace("=", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Carácter Base32 inválido: " + c);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
