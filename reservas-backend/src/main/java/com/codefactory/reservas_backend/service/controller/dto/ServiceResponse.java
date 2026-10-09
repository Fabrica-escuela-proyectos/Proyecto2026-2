package com.codefactory.reservas_backend.service.controller.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ServiceResponse {
    private final UUID id;
    private final UUID businessId;
    private final String name;
    private final String description;
    private final int durationMinutes;
    private final long priceCop;
    private final boolean active;
    private final Instant createdAt;
}
