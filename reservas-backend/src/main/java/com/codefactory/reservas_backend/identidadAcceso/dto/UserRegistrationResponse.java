package com.codefactory.reservas_backend.identidadAcceso.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
 
import java.util.UUID;
 
@Getter
@AllArgsConstructor
public class UserRegistrationResponse {
    private UUID id;
    private String fullName;
    private String email;
    private String role;
}
