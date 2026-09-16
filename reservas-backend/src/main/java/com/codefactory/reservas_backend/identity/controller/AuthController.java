package com.codefactory.reservas_backend.identity.controller;

import com.codefactory.reservas_backend.identity.application.AuthService;
import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.controller.dto.LoginRequest;
import com.codefactory.reservas_backend.identity.controller.dto.LoginResponse;
import com.codefactory.reservas_backend.identity.domain.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-02 - Inicio de sesión y HU-04 - Cerrar sesión.
 * Contratos: POST /api/v1/auth/login y POST /api/v1/auth/logout
 * (endpoints-sprint-1.md secciones 3 y 5).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final IdentityService identityService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    // Requiere autenticación: no está en la lista permitAll de
    // SecurityConfig, así que anyRequest().authenticated() ya exige un JWT
    // válido con una sesión vigente antes de llegar aquí.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        var current = identityService.getCurrentUser()
                .orElseThrow(() -> new InvalidCredentialsException("No hay una sesión activa"));
        authService.logout(current.id(), httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
