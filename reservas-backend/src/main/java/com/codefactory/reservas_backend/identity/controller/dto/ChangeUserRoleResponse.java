package com.codefactory.reservas_backend.identity.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ChangeUserRoleResponse {
    private UUID userId;
    private String email;
    private String role;
}
