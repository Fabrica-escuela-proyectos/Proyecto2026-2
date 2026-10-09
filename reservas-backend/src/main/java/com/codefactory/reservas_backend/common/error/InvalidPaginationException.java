package com.codefactory.reservas_backend.common.error;

/** Parámetros de paginación fuera de rango (página negativa o tamaño menor que 1). Mapea a 400. */
public class InvalidPaginationException extends RuntimeException {
    public InvalidPaginationException(String message) {
        super(message);
    }
}
