package com.codefactory.reservas_backend.identity.application;

import java.util.UUID;

/**
 * Evento de dominio que Identity publica, de forma síncrona y DENTRO de la transacción de eliminación,
 * justo antes de borrar a un usuario (HU-28). Otros módulos lo escuchan para dejar en orden lo suyo
 * (Reservation cancela las reservas futuras del usuario o de su negocio) sin que Identity los importe
 * (ADR-003). Si un oyente falla, la excepción deshace también la eliminación: todo o nada.
 *
 * @param role rol principal del usuario eliminado (CLIENTE o PROVEEDOR), solo informativo
 */
public record UserDeletionRequested(UUID userId, String email, String role) {
}
