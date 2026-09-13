package com.codefactory.reservas_backend.identidadAcceso.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
 
/**
 * Formato exigido por HU-01. Supuesto tomado del unico celular valido que
 * aparece en la propia HU ("3001234567"): celular colombiano, 10 digitos,
 * inicia en 3. Los ejemplos invalidos del Scenario Outline ("abcde12345",
 * "12345", "123") quedan cubiertos porque no cumplen el patron.
 *
 * IMPORTANTE (marcar en docs/matriz-actualizaciones.md): HU-01 no dice de
 * forma explicita "celular colombiano"; este es un supuesto razonable que
 * debe confirmarse con el equipo antes de darlo por definitivo, porque si el
 * alcance del proyecto permite otros paises el patron cambiaria.
 */
public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {
 
    private static final String PHONE_REGEX = "^3\\d{9}$";
 
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        return value.matches(PHONE_REGEX);
    }
}
