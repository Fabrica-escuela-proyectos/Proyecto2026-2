package com.codefactory.reservas_backend.resource.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Un rango horario de atención de un recurso en un día de la semana (HU-19).
 * {@code dayOfWeek}: 1 = lunes ... 7 = domingo (ISO-8601). Rango semiabierto
 * [startTime, endTime), en hora local del negocio.
 */
@Entity
@Table(name = "resource_availability")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
