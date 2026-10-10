package com.codefactory.reservas_backend.reservation.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Reserva de un servicio en un recurso y un horario (HU-22). Guarda copias
 * ("snapshots") de los nombres y el precio vigentes al reservar para que el
 * historial sobreviva a ediciones o eliminaciones. Referencia a cliente,
 * negocio, servicio y recurso solo por id (ADR-003).
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", updatable = false)
    private UUID clientId;

    @Column(name = "client_email", nullable = false, length = 150)
    private String clientEmail;

    @Column(name = "client_name", nullable = false, length = 150)
    private String clientName;

    @Column(name = "business_id", updatable = false)
    private UUID businessId;

    @Column(name = "business_name", nullable = false, length = 150)
    private String businessName;

    @Column(name = "service_id", updatable = false)
    private UUID serviceId;

    @Column(name = "service_name", nullable = false, length = 150)
    private String serviceName;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "resource_name", nullable = false, length = 150)
    private String resourceName;

    @Column(name = "start_at", nullable = false, updatable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false, updatable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "price_cop", nullable = false, updatable = false)
    private long priceCop;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_origin", length = 30)
    private CancelOrigin cancelOrigin;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
