package com.codefactory.reservas_backend.identity.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.StepUpService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.identity.application.UserManagementService;
import com.codefactory.reservas_backend.identity.application.UserRegistrationService;
import com.codefactory.reservas_backend.identity.controller.dto.ChangeUserRoleRequest;
import com.codefactory.reservas_backend.identity.controller.dto.ChangeUserRoleResponse;
import com.codefactory.reservas_backend.identity.controller.dto.RegisterUserRequest;
import com.codefactory.reservas_backend.identity.controller.dto.RegisterUserResponse;
import com.codefactory.reservas_backend.identity.controller.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-01 - Registrar cliente (POST, sin cambios), más HU-05 - Gestionar
 * roles y permisos (PATCH .../role, DELETE) y el ejemplo de endpoint
 * protegido de HU-06 (GET .../{userId}) — endpoints-sprint-1.md secciones
 * 2, 6 y 7.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRegistrationService userRegistrationService;
    private final UserManagementService userManagementService;
    private final IdentityService identityService;
    private final StepUpService stepUpService;

    @PostMapping
    public ResponseEntity<RegisterUserResponse> register(
            @Valid @RequestBody RegisterUserRequest request,
            HttpServletRequest httpRequest) {

        String originIp = httpRequest.getRemoteAddr();
        RegisterUserResponse response = userRegistrationService.register(request, originIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(userManagementService.getUser(userId, currentUser()));
    }

    // Operación sensible (HU-02, "Verificación adicional para operaciones
    // sensibles"; ADR-004 P6): además del rol, exige el código MFA vigente del
    // administrador en el header X-MFA-Code.
    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ChangeUserRoleResponse> changeRole(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeUserRoleRequest request,
            @RequestHeader(value = StepUpService.MFA_CODE_HEADER, required = false) String mfaCode,
            HttpServletRequest httpRequest) {

        UserIdentity admin = currentUser();
        stepUpService.requireValidCode(admin, mfaCode, "CAMBIO_ROL", httpRequest.getRemoteAddr());
        ChangeUserRoleResponse response = userManagementService.changeRole(
                userId, request.getRole(), admin, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID userId,
            @RequestHeader(value = StepUpService.MFA_CODE_HEADER, required = false) String mfaCode,
            HttpServletRequest httpRequest) {

        UserIdentity admin = currentUser();
        stepUpService.requireValidCode(admin, mfaCode, "ELIMINACION_USUARIO", httpRequest.getRemoteAddr());
        userManagementService.deleteUser(userId, admin, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private UserIdentity currentUser() {
        return identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
    }
}
