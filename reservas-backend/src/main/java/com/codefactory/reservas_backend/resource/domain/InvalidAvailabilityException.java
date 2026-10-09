package com.codefactory.reservas_backend.resource.domain;

/**
 * HU-19: horario inválido (inicio >= fin, día fuera de 1..7, días repetidos o
 * rangos superpuestos). Mapea a 400; el recurso conserva su disponibilidad anterior.
 */
public class InvalidAvailabilityException extends RuntimeException {
    public InvalidAvailabilityException(String message) {
        super(message);
    }
}
