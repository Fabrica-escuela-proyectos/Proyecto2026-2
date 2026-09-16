package com.codefactory.reservas_backend.common.error;

import com.codefactory.reservas_backend.identity.domain.AdminDeletionNotAllowedException;
import com.codefactory.reservas_backend.identity.domain.DuplicateEmailException;
import com.codefactory.reservas_backend.identity.domain.DuplicatePhoneException;
import com.codefactory.reservas_backend.identity.domain.InvalidCredentialsException;
import com.codefactory.reservas_backend.identity.domain.InvalidMfaCodeException;
import com.codefactory.reservas_backend.identity.domain.MfaNotConfiguredException;
import com.codefactory.reservas_backend.identity.domain.ProviderRoleImmutableException;
import com.codefactory.reservas_backend.identity.domain.RoleNotFoundException;
import com.codefactory.reservas_backend.identity.domain.SelfModificationException;
import com.codefactory.reservas_backend.identity.domain.UserNotFoundException;
import com.codefactory.reservas_backend.identity.infrastructure.TooManyRequestsException;
import com.codefactory.reservas_backend.provider.domain.ProviderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    // HU-02: credenciales inválidas, cuenta deshabilitada o código MFA
    // faltante/incorrecto en el login — errores-api-sprint-1.md sección 5.
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), null, req);
    }

    // Errores de validación de negocio que no vienen de Bean Validation
    // (@Valid), pero que igualmente son "datos de entrada inválidos":
    // rol inexistente (HU-05) y código/estado de MFA inválido. Se
    // mantienen dentro del vocabulario de errores documentado
    // (errores-api-sprint-1.md sección 3) en vez de inventar un nuevo
    // "error" además de los ya definidos.
    @ExceptionHandler({RoleNotFoundException.class, InvalidMfaCodeException.class, MfaNotConfiguredException.class})
    public ResponseEntity<ApiError> handleBusinessValidation(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), null, req);
    }

    // HU-05/HU-06: el usuario/proveedor objetivo de la operación no existe.
    @ExceptionHandler({UserNotFoundException.class, ProviderNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), null, req);
    }

    // HU-05: autenticado pero sin permiso suficiente para ESTA operación
    // específica (auto-modificación, rol de Proveedor inmutable, borrado de
    // Administrador). Mensajes propios y específicos porque el usuario ya
    // sabe qué intentó hacer; no hay riesgo de revelar información nueva.
    @ExceptionHandler({SelfModificationException.class, ProviderRoleImmutableException.class,
            AdminDeletionNotAllowedException.class})
    public ResponseEntity<ApiError> handleForbidden(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), null, req);
    }

    // HU-06: denegación genérica de pertenencia/rol (UserManagementService,
    // ProviderQueryService, y cualquier @PreAuthorize que falle dentro del
    // despachador MVC). Se usa el mismo mensaje genérico de
    // RestAccessDeniedHandler en vez de ex.getMessage(): cuando la excepción
    // la lanza Spring Security directamente (p. ej. @PreAuthorize) su
    // mensaje por defecto está en inglés ("Access Denied"), y mezclar
    // idiomas en la respuesta rompería el formato uniforme.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tiene permisos para realizar esta operación", null, req);
    }

    // Red de seguridad genérica (errores-api-sprint-1.md sección 10): nunca
    // exponer stack traces, SQL ni detalles internos en la respuesta.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        // El cliente nunca ve el detalle (sección 10), pero sin loguearlo
        // server-side un 500 real sería indiagnosticable en producción.
        log.error("Error interno no controlado en {} {}", req.getMethod(), req.getRequestURI(), ex);
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
