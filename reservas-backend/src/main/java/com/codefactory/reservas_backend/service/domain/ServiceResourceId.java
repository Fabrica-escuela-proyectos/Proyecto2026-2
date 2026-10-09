package com.codefactory.reservas_backend.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** Clave compuesta de {@link ServiceResource}: un recurso se asigna una sola vez a un servicio. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ServiceResourceId implements Serializable {

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;
}
