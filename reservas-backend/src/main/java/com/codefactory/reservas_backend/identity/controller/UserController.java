package com.codefactory.reservas_backend.identity.controller;

import com.codefactory.reservas_backend.identity.application.UserRegistrationService;
import com.codefactory.reservas_backend.identity.controller.dto.RegisterUserRequest;
import com.codefactory.reservas_backend.identity.controller.dto.RegisterUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-01 - Registrar cliente.
 * Contrato: POST /api/v1/users (endpoints-sprint-1.md sección 2).
 * Detalle completo del contrato en docs/api-contract-POST-users.md.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRegistrationService userRegistrationService;

    @PostMapping
    public ResponseEntity<RegisterUserResponse> register(
            @Valid @RequestBody RegisterUserRequest request,
            HttpServletRequest httpRequest) {

        String originIp = httpRequest.getRemoteAddr();
        RegisterUserResponse response = userRegistrationService.register(request, originIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
