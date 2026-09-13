package com.codefactory.reservas_backend.identidadAcceso.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
 
/**
 * Politica exigida por HU-01 (Scenario Outline "Contrasena que no cumple la
 * politica de seguridad"): minimo 8 caracteres, mayuscula, minuscula y un
 * caracter especial. Los ejemplos dados en la HU (si, *, +, grado) no son
 * un set cerrado, por eso se valida "cualquier caracter no alfanumerico" en
 * vez de una lista fija.
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {
 
    private static final int MIN_LENGTH = 8;
    private static final String UPPERCASE = ".*[A-ZÁÉÍÓÚÑ].*";
    private static final String LOWERCASE = ".*[a-záéíóúñ].*";
    private static final String SPECIAL_CHAR = ".*[^a-zA-Z0-9].*";
 
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.length() < MIN_LENGTH) {
            return false;
        }
        return value.matches(UPPERCASE) && value.matches(LOWERCASE) && value.matches(SPECIAL_CHAR);
    }
}
