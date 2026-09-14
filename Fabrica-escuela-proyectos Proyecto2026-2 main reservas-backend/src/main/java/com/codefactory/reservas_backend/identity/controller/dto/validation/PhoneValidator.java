package com.codefactory.reservas_backend.identity.controller.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Formato exigido por HU-01. Supuesto tomado del único celular válido que
 * aparece en la propia HU ("3001234567"): celular colombiano, 10 dígitos,
 * inicia en 3. Los ejemplos inválidos del Scenario Outline ("abcde12345",
 * "12345", "123") quedan cubiertos porque no cumplen el patrón.
 *
 * IMPORTANTE (ver docs/matriz-actualizaciones.md): ni HU-01 ni los nuevos
 * documentos de arquitectura (dtos-sprint-1.md, endpoints-sprint-1.md)
 * dicen de forma explícita "celular colombiano"; sigue siendo un supuesto
 * razonable a confirmar con el equipo, no una decisión cerrada.
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
