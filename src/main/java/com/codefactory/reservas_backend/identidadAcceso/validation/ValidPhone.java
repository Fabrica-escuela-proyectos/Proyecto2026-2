package com.codefactory.reservas_backend.identidadAcceso.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
 
import java.lang.annotation.*;
 
@Documented
@Constraint(validatedBy = PhoneValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {
    String message() default "El numero de celular no es valido";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
