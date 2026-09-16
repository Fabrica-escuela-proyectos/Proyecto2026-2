package com.codefactory.reservas_backend.identity.controller.dto;

import com.codefactory.reservas_backend.identity.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta de GET /api/v1/users/{userId} (ejemplo de endpoint protegido de
 * HU-06, endpoints-sprint-1.md sección 7). Expone únicamente los campos
 * seguros de User — nunca passwordHash (dtos-sprint-1.md sección 11: "los
 * datos internos de las entidades de persistencia no serán expuestos
 * directamente mediante los DTOs").
 */
@Getter
@Builder
public class UserResponse {
    private UUID id;
    private String fullName;
    private String email;
    private String cellphone;
    private String role;
    private boolean enabled;
    private Instant createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .cellphone(user.getCellphone())
                .role(user.getRoles().stream().findFirst().map(r -> r.getName().name()).orElse(null))
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
