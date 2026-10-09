package com.codefactory.reservas_backend.resource.controller.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ResourceResponse {
    private final UUID id;
    private final UUID businessId;
    private final String name;
    private final String type;
    private final boolean active;
    private final Instant createdAt;
}
