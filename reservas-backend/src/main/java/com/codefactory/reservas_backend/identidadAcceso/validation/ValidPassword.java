package com.codefactory.reservas_backend.identidadAcceso.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
 
import java.lang.annotation.*;
 
@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {
    String message() default "La contrasena no cumple la politica de seguridad";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}