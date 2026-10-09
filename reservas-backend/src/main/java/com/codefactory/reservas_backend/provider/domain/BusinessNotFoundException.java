package com.codefactory.reservas_backend.provider.domain;

/**
 * El negocio indicado en la ruta no existe (HU-09 y las HU que cuelgan de
 * /businesses/{businessId}). Mapea a 404.
 */
public class BusinessNotFoundException extends RuntimeException {
    public BusinessNotFoundException(String message) {
        super(message);
    }
}
