package com.codefactory.reservas_backend.common.error;

import com.codefactory.reservas_backend.identidadAcceso.exception.DuplicateEmailException;
import com.codefactory.reservas_backend.identidadAcceso.exception.DuplicatePhoneException;
import com.codefactory.reservas_backend.identidadAcceso.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
 
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
 
@RestControllerAdvice
public class GlobalExceptionHandler {
 
    // Escenarios "Formato de correo invalido", "Numero de celular con
    // formato invalido", "Contrasena que no cumple politica" y "Campo
    // obligatorio faltante" llegan todos aqui via Bean Validation.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Uno o mas campos no son validos", details, req);
    }
 
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiError> handleDuplicateEmail(DuplicateEmailException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "EMAIL_ALREADY_IN_USE", ex.getMessage(), null, req);
    }
 
    @ExceptionHandler(DuplicatePhoneException.class)
    public ResponseEntity<ApiError> handleDuplicatePhone(DuplicatePhoneException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "PHONE_ALREADY_IN_USE", ex.getMessage(), null, req);
    }
 
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest req) {
        return build(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REGISTRATION_ATTEMPTS", ex.getMessage(), null, req);
    }
 
    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message, List<String> details, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString();
        ApiError error = new ApiError(code, message, details, traceId, Instant.now());
        return ResponseEntity.status(status).body(error);
    }
}
