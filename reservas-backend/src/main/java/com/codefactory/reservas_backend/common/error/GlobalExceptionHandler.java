package com.codefactory.reservas_backend.common.error;

import com.codefactory.reservas_backend.identity.domain.DuplicateEmailException;
import com.codefactory.reservas_backend.identity.domain.DuplicatePhoneException;
import com.codefactory.reservas_backend.identity.infrastructure.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manejador centralizado de excepciones (errores-api-sprint-1.md sección
 * 11: "@RestControllerAdvice"). Construye todas las respuestas de error
 * con el esquema {timestamp, status, error, message, path[, fields]}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Escenarios "Formato de correo inválido", "Número de celular con
    // formato inválido", "Contraseña que no cumple política" y "Campo
    // obligatorio faltante" llegan todos aquí vía Bean Validation.
    // errores-api-sprint-1.md sección 4: los errores de campo van en un
    // mapa "fields", no en una lista de strings.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage(),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Los datos enviados no son válidos", fields, req);
    }

    // errores-api-sprint-1.md sección 8 da un mensaje genérico único para
    // ambos tipos de conflicto ("No es posible completar el registro con
    // los datos proporcionados"), pero HU-01 define dos escenarios Gherkin
    // distintos (correo duplicado / celular duplicado) que necesitan poder
    // distinguirse. Se mantiene el "error": "CONFLICT" del esquema oficial,
    // pero con un "message" específico por campo — desviación documentada
    // en docs/matriz-actualizaciones.md, a confirmar con el equipo.
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiError> handleDuplicateEmail(DuplicateEmailException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), null, req);
    }

    @ExceptionHandler(DuplicatePhoneException.class)
    public ResponseEntity<ApiError> handleDuplicatePhone(DuplicatePhoneException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), null, req);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest req) {
        return build(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", ex.getMessage(), null, req);
    }

    // Red de seguridad genérica (errores-api-sprint-1.md sección 10): nunca
    // exponer stack traces, SQL ni detalles internos en la respuesta.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "Ocurrió un error interno al procesar la solicitud", null, req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message,
                                            Map<String, String> fields, HttpServletRequest req) {
        ApiError apiError = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(req.getRequestURI())
                .fields(fields)
                .build();
        return ResponseEntity.status(status).body(apiError);
    }
}
