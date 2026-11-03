package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.provider.domain.Business;

import java.time.Instant;
import java.util.UUID;

/** Datos públicos completos de un negocio (HU-07); los opcionales vienen nulos si no se diligenciaron. */
public record BusinessProfile(UUID id, String name, String description, String address, String businessType,
                              String phone, Instant updatedAt) {

    static BusinessProfile of(Business b) {
        return new BusinessProfile(b.getId(), b.getName(), b.getDescription(), b.getAddress(),
                b.getBusinessType() == null ? null : b.getBusinessType().name(), b.getPhone(), b.getUpdatedAt());
    }
}
