package com.codefactory.reservas_backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * Política exigida por HU-01 (Scenario Outline "Contraseña que no cumple la
 * política de seguridad") y confirmada en dtos-sprint-1.md sección 8:
 * mínimo 8 caracteres, mayúscula, minúscula y un carácter especial. Los
 * ejemplos dados en la HU (¡, *, +, °) no son un set cerrado, por eso se
 * valida "cualquier carácter no alfanumérico" en vez de una lista fija.
 *
 * Movido de identity.controller.dto.validation a common.validation cuando
 * HU-03 (registro de proveedor) empezó a necesitar la misma política de
 * contraseña: ambos módulos comparten esta regla porque las dos historias
 * crean cuentas de acceso, no porque provider dependa de identity.
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_LENGTH = 8;
    // BCrypt solo usa los primeros 72 BYTES: una contraseña más larga (o con
    // pocos caracteres multibyte, como "ñ" o emojis, que ya suman 72 bytes)
    // se truncaría en silencio o la rechazaría el codificador (issue #9).
    private static final int MAX_BYTES = 72;
    private static final String UPPERCASE = ".*[A-ZÁÉÍÓÚÑ].*";
    private static final String LOWERCASE = ".*[a-záéíóúñ].*";
    private static final String SPECIAL_CHAR = ".*[^a-zA-Z0-9].*";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.length() < MIN_LENGTH
                || value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return false;
        }
        return value.matches(UPPERCASE) && value.matches(LOWERCASE) && value.matches(SPECIAL_CHAR);
    }
}
