package com.codefactory.reservas_backend.service.domain;

/**
 * HU-18: alguno de los recursos indicados no existe o no pertenece al negocio
 * del servicio. Mapea a 400. Un solo mensaje para ambos casos, para no revelar
 * si un id corresponde a un recurso de otro negocio.
 */
public class InvalidResourceAssignmentException extends RuntimeException {
    public InvalidResourceAssignmentException(String message) {
        super(message);
    }
}
