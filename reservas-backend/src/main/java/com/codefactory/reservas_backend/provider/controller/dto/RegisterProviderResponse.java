package com.codefactory.reservas_backend.provider.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class RegisterProviderResponse {
    private UUID userId;
    private String fullName;
    private String email;
    private String role;
    private UUID providerId;
    private UUID businessId;
    private String businessName;
}
