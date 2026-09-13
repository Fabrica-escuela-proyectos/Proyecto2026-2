package com.codefactory.reservas_backend.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Formato uniforme de error definido en errores-api-sprint-1.md sección 2,
 * que REEMPLAZA el formato propio (errorCode/details/traceId) usado antes
 * de este refactor. Todo controlador nuevo (HU02+) debe reutilizar este
 * mismo formato vía GlobalExceptionHandler.
 *
 * {@code fields} solo se incluye en errores de validación (sección 4); se
 * omite del JSON en el resto de los casos gracias a
 * {@code @JsonInclude(NON_NULL)}, tal como muestran los ejemplos del
 * documento fuente.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final Map<String, String> fields;
}
