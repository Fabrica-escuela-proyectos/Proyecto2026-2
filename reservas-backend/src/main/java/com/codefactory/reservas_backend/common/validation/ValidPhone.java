package com.codefactory.reservas_backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PhoneValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {
    String message() default "El número de celular no es válido";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
