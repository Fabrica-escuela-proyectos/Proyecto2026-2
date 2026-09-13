package com.codefactory.reservas_backend.common.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
 
import java.time.Instant;
import java.util.List;
 
/**
 * Formato uniforme de error exigido por Lineamientos Sec. 3.3:
 * errorCode, message, details y traceId. Todo controlador nuevo (HU02+)
 * debe reutilizar este mismo formato via GlobalExceptionHandler.
 */
@Getter
@AllArgsConstructor
public class ApiError {
    private String errorCode;
    private String message;
    private List<String> details;
    private String traceId;
    private Instant timestamp;
}
