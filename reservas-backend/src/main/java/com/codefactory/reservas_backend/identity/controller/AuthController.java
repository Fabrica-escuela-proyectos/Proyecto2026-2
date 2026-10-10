package com.codefactory.reservas_backend.identity.controller;

import com.codefactory.reservas_backend.identity.application.AuthService;
import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.controller.dto.LoginRequest;
import com.codefactory.reservas_backend.identity.controller.dto.LoginResponse;
import com.codefactory.reservas_backend.identity.domain.InvalidCredentialsException;
import com.codefactory.reservas_backend.common.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = OpenApiTags.AUTH)
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final IdentityService identityService;

    @Operation(summary = "Iniciar sesión (HU-02)",
            description = "Devuelve un JWT de 1 hora. Si la cuenta tiene MFA activa (obligatoria para administradores) hay que enviar también `mfaCode` (código TOTP de 6 dígitos); sin él responde 401 con error MFA_REQUIRED. Cinco intentos fallidos en 15 minutos bloquean al usuario durante 15 minutos (429).")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Sesión iniciada: token y datos del usuario")
    @ApiResponse(responseCode = "400", description = "Campos obligatorios ausentes o demasiado largos")
    @ApiResponse(responseCode = "401", description = "Credenciales inválidas (mensaje genérico) o falta el código MFA")
    @ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    // Requiere autenticación: no está en la lista permitAll de
    // SecurityConfig, así que anyRequest().authenticated() ya exige un JWT
    // válido con una sesión vigente antes de llegar aquí.
    @Operation(summary = "Cerrar sesión (HU-04)",
            description = "Revoca el token actual: queda inutilizable aunque no haya expirado.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        var current = identityService.getCurrentUser()
                .orElseThrow(() -> new InvalidCredentialsException("No hay una sesión activa"));
        authService.logout(current.id(), httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
