package com.codefactory.reservas_backend.reservation.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lectura agregada de las reservas para los reportes del Sprint 3 (HU-29..34). Es la única puerta del
 * módulo de reportes a las reservas (ADR-008): devuelve números y registros simples, nunca entidades, y las
 * sumas y conteos los hace la base. El periodo es [from, to) sobre el INICIO de la reserva.
 * Con {@code businessId} nulo se consulta toda la plataforma (HU-34).
 */
public interface BookingReportQuery {

    /** Reservas por estado (CONFIRMADA, CANCELADA, COMPLETADA); los estados sin reservas no aparecen. */
    Map<String, Long> countByStatus(UUID businessId, Instant from, Instant to);

    /** Reservas CANCELADAS por origen (CLIENTE, PROVEEDOR, ...). */
    Map<String, Long> cancelledByOrigin(UUID businessId, Instant from, Instant to);

    /** Suma del precio guardado en cada reserva (snapshot), por estado. */
    Map<String, Long> valueByStatus(UUID businessId, Instant from, Instant to);

    /** Reservas por servicio de un negocio: las que cuentan como reservadas (confirmadas + completadas) y las canceladas. */
    List<ServiceCount> countByService(UUID businessId, Instant from, Instant to);

    /** Minutos ocupados (confirmadas + completadas) por recurso y día de Bogotá del inicio de la reserva. */
    List<OccupiedMinutes> occupiedMinutesByDay(UUID businessId, Instant from, Instant to);

    record ServiceCount(UUID serviceId, String serviceName, long booked, long cancelled) {
    }

    record OccupiedMinutes(UUID resourceId, LocalDate date, long minutes) {
    }
}
