package com.codefactory.reservas_backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

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
    // Se busca con find() sin ".*" al inicio y al final: evita el backtracking
    // super-lineal que Sonar señala (S8786) y significa lo mismo.
    private static final Pattern UPPERCASE = Pattern.compile("[A-ZÁÉÍÓÚÑ]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-záéíóúñ]");
    private static final Pattern SPECIAL_CHAR = Pattern.compile("[^a-zA-Z0-9]");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.length() < MIN_LENGTH
                || value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return false;
        }
        return UPPERCASE.matcher(value).find() && LOWERCASE.matcher(value).find()
                && SPECIAL_CHAR.matcher(value).find();
    }
}
