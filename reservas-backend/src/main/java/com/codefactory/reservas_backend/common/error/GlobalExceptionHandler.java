package com.codefactory.reservas_backend.common.error;

import com.codefactory.reservas_backend.identity.domain.AdminDeletionNotAllowedException;
import com.codefactory.reservas_backend.identity.domain.DuplicateEmailException;
import com.codefactory.reservas_backend.identity.domain.DuplicatePhoneException;
import com.codefactory.reservas_backend.identity.domain.InvalidCredentialsException;
import com.codefactory.reservas_backend.identity.domain.InvalidMfaCodeException;
import com.codefactory.reservas_backend.identity.domain.MfaEnrollmentRequiredException;
import com.codefactory.reservas_backend.identity.domain.MfaNotConfiguredException;
import com.codefactory.reservas_backend.identity.domain.MfaRequiredException;
import com.codefactory.reservas_backend.identity.domain.ProviderRoleImmutableException;
import com.codefactory.reservas_backend.identity.domain.RoleNotFoundException;
import com.codefactory.reservas_backend.identity.domain.SelfModificationException;
import com.codefactory.reservas_backend.identity.domain.UserNotFoundException;
import com.codefactory.reservas_backend.identity.infrastructure.TooManyRequestsException;
import com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException;
import com.codefactory.reservas_backend.provider.domain.ProviderNotFoundException;
import com.codefactory.reservas_backend.resource.domain.DuplicateResourceNameException;
import com.codefactory.reservas_backend.service.domain.DuplicateServiceNameException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manejador centralizado de excepciones (errores-api-sprint-1.md sección
 * 11: "@RestControllerAdvice"). Construye todas las respuestas de error
 * con el esquema {timestamp, status, error, message, path[, fields]}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // SQLSTATE de PostgreSQL que se traducen a respuestas de cliente.
    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";
    private static final String SQLSTATE_VALUE_TOO_LONG = "22001";
    private static final Pattern CONSTRAINT_IN_MESSAGE = Pattern.compile("constraint \"([^\"]+)\"");

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

    // HU-09: nombre de servicio repetido dentro del mismo negocio.
    // HU-14: lo mismo para el nombre de un recurso dentro del mismo negocio.
    @ExceptionHandler({DuplicateServiceNameException.class, DuplicateResourceNameException.class})
    public ResponseEntity<ApiError> handleDuplicateService(RuntimeException ex, HttpServletRequest req) {
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

    // ADR-004 (P5/P6): contraseña válida pero falta el código MFA (login) o el
    // header X-MFA-Code (operación sensible). Código propio para que el
    // cliente sepa que debe pedir el código; un código incorrecto sigue siendo
    // el 401 genérico de InvalidCredentialsException.
    @ExceptionHandler(MfaRequiredException.class)
    public ResponseEntity<ApiError> handleMfaRequired(MfaRequiredException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "MFA_REQUIRED", ex.getMessage(), null, req);
    }

    // ADR-004 (P4): administrador sin MFA activa fuera de /auth/mfa/** (la
    // ruta normal la bloquea MfaEnrollmentFilter; esto cubre el step-up).
    @ExceptionHandler(MfaEnrollmentRequiredException.class)
    public ResponseEntity<ApiError> handleMfaEnrollmentRequired(MfaEnrollmentRequiredException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "MFA_ENROLLMENT_REQUIRED", ex.getMessage(), null, req);
    }

    // Errores de validación de negocio que no vienen de Bean Validation
    // (@Valid), pero que igualmente son "datos de entrada inválidos":
    // rol inexistente (HU-05) y código/estado de MFA inválido. Se
    // mantienen dentro del vocabulario de errores documentado
    // (errores-api-sprint-1.md sección 3) en vez de inventar un nuevo
    // "error" además de los ya definidos.
    @ExceptionHandler({RoleNotFoundException.class, InvalidMfaCodeException.class, MfaNotConfiguredException.class,
            InvalidPaginationException.class})
    public ResponseEntity<ApiError> handleBusinessValidation(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), null, req);
    }

    // HU-05/HU-06: el usuario/proveedor objetivo de la operación no existe.
    @ExceptionHandler({UserNotFoundException.class, ProviderNotFoundException.class, BusinessNotFoundException.class})
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

    // Issue #10: la verificación "¿existe el correo?" y el INSERT no son
    // atómicos, así que dos registros simultáneos pueden llegar a la BD y
    // perder contra la restricción única. Esa violación (SQLSTATE 23505) es un
    // conflicto del cliente, no un error interno.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        String sqlState = sqlStateOf(ex);
        if (SQLSTATE_UNIQUE_VIOLATION.equals(sqlState)) {
            return build(HttpStatus.CONFLICT, "CONFLICT", uniqueViolationMessage(violatedConstraintOf(ex)), null, req);
        }
        if (SQLSTATE_VALUE_TOO_LONG.equals(sqlState)) {
            return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Alguno de los valores enviados supera la longitud permitida", null, req);
        }
        return handleUnexpected(ex, req);
    }

    // Issue #11: cuerpo vacío o JSON mal formado. El mensaje es fijo a
    // propósito: el detalle del parser (Jackson) no se expone al cliente.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "El cuerpo de la solicitud es inválido o está vacío", null, req);
    }

    // Issue #11: UUID o número mal formado en la ruta o en un parámetro
    // (p. ej. GET /api/v1/users/abc). Se nombra el parámetro, nunca su valor.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "El parámetro '" + ex.getName() + "' tiene un formato inválido", null, req);
    }

    // Faltan parámetros de consulta, cabeceras o variables de ruta obligatorios.
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiError> handleMissingRequestValue(ServletRequestBindingException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Falta un parámetro o cabecera obligatoria de la solicitud", null, req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        HttpHeaders headers = new HttpHeaders();
        Set<HttpMethod> allowed = ex.getSupportedHttpMethods();
        if (allowed != null && !allowed.isEmpty()) {
            headers.setAllow(allowed);
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers).body(
                body(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                        "El método HTTP no está permitido para este recurso", null, req));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "El tipo de contenido no es compatible; use application/json", null, req);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
                "El servidor solo responde en application/json", null, req);
    }

    // Ruta que no corresponde a ningún endpoint (con sesión válida; sin
    // sesión, SecurityConfig responde 401 antes de llegar aquí).
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> handleNoHandler(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "El recurso solicitado no existe", null, req);
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
        return ResponseEntity.status(status).body(body(status, error, message, fields, req));
    }

    private ApiError body(HttpStatus status, String error, String message,
                          Map<String, String> fields, HttpServletRequest req) {
        return ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(req.getRequestURI())
                .fields(fields)
                .build();
    }

    // --- Lectura del error de BD detrás de una DataIntegrityViolationException ---

    private static String sqlStateOf(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    /** Nombre de la restricción violada, tomado del mensaje de PostgreSQL ("... constraint "uk_users_email""). */
    private static String violatedConstraintOf(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t.getMessage() != null) {
                Matcher m = CONSTRAINT_IN_MESSAGE.matcher(t.getMessage());
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        return null;
    }

    // Mismos mensajes que DuplicateEmailException / DuplicatePhoneException,
    // para que el cliente reciba lo mismo gane o pierda la carrera.
    private static String uniqueViolationMessage(String constraint) {
        if ("uk_users_email".equals(constraint)) {
            return "El correo electrónico ya está en uso";
        }
        if ("uk_users_phone".equals(constraint)) {
            return "El número de celular ya está en uso";
        }
        if ("uk_services_business_name".equals(constraint)) {
            return "Ya existe un servicio con ese nombre en el negocio";
        }
        if ("uk_resources_business_name".equals(constraint)) {
            return "Ya existe un recurso con ese nombre en el negocio";
        }
        return "Ya existe un registro con los datos enviados";
    }
}
