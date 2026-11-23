package com.codefactory.reservas_backend.e2e.models;

/**
 * Lo que un proveedor deja listo para recibir reservas: su negocio, un servicio con precio y duración,
 * y un recurso asignado a ese servicio con horario de atención.
 */
public record Agenda(String idDelNegocio, String nombreDelNegocio, String idDelServicio, String nombreDelServicio,
                     String idDelRecurso, String nombreDelRecurso, int duracionEnMinutos, long precio) {
}
