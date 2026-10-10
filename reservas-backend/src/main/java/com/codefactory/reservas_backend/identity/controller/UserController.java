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
import com.codefactory.reservas_backend.common.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = OpenApiTags.USERS)
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRegistrationService userRegistrationService;
    private final UserManagementService userManagementService;
    private final IdentityService identityService;
    private final StepUpService stepUpService;

    @Operation(summary = "Registrar un cliente (HU-01)",
            description = "Crea una cuenta con rol CLIENTE. Límite: 5 registros por IP cada 10 minutos (compartido con el registro de proveedores).")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Cliente creado")
    @ApiResponse(responseCode = "400", description = "Campos obligatorios ausentes, formato inválido o contraseña que no cumple la política")
    @ApiResponse(responseCode = "409", description = "El correo o el celular ya están registrados")
    @ApiResponse(responseCode = "429", description = "Límite de registros por IP alcanzado")
    @PostMapping
    public ResponseEntity<RegisterUserResponse> register(
            @Valid @RequestBody RegisterUserRequest request,
            HttpServletRequest httpRequest) {

        String originIp = httpRequest.getRemoteAddr();
        RegisterUserResponse response = userRegistrationService.register(request, originIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Consultar un usuario (HU-06)",
            description = "Un usuario solo puede consultarse a sí mismo; un administrador puede consultar a cualquiera.")
    @ApiResponse(responseCode = "200", description = "Datos del usuario")
    @ApiResponse(responseCode = "403", description = "Es la cuenta de otro usuario y quien consulta no es administrador")
    @ApiResponse(responseCode = "404", description = "El usuario no existe")
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(userManagementService.getUser(userId, currentUser()));
    }

    // Operación sensible (HU-02, "Verificación adicional para operaciones
    // sensibles"; ADR-004 P6): además del rol, exige el código MFA vigente del
    // administrador en el header X-MFA-Code.
    @Operation(summary = "Cambiar el rol de un usuario (HU-05)",
            description = "Solo ADMINISTRADOR. Operación sensible: exige el código TOTP vigente en el header X-MFA-Code. No se puede cambiar el propio rol ni el de un proveedor.")
    @Parameter(name = "X-MFA-Code", in = ParameterIn.HEADER, required = true, description = "Código TOTP vigente del administrador (6 dígitos)", example = "123456")
    @ApiResponse(responseCode = "200", description = "Rol actualizado")
    @ApiResponse(responseCode = "400", description = "Rol inexistente o código MFA inválido")
    @ApiResponse(responseCode = "401", description = "Sin sesión, o falta el código MFA (error MFA_REQUIRED)")
    @ApiResponse(responseCode = "403", description = "No es administrador, intenta cambiar su propio rol o el de un proveedor, o no ha enrolado su MFA")
    @ApiResponse(responseCode = "404", description = "El usuario no existe")
    @ApiResponse(responseCode = "429", description = "Demasiados códigos MFA fallidos")
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

    @Operation(summary = "Eliminar una cuenta (HU-05, HU-28)",
            description = "Solo ADMINISTRADOR, con el código TOTP en X-MFA-Code. Cancela las reservas futuras del usuario (y las de todos sus negocios si es proveedor) con origen ELIMINACION_CUENTA; las pasadas se conservan como historial. No se puede eliminar la propia cuenta ni la de otro administrador.")
    @Parameter(name = "X-MFA-Code", in = ParameterIn.HEADER, required = true, description = "Código TOTP vigente del administrador (6 dígitos)", example = "123456")
    @ApiResponse(responseCode = "204", description = "Cuenta eliminada")
    @ApiResponse(responseCode = "400", description = "Código MFA inválido")
    @ApiResponse(responseCode = "401", description = "Sin sesión, o falta el código MFA (error MFA_REQUIRED)")
    @ApiResponse(responseCode = "403", description = "No es administrador, intenta eliminarse a sí mismo o a otro administrador, o no ha enrolado su MFA")
    @ApiResponse(responseCode = "404", description = "El usuario no existe")
    @ApiResponse(responseCode = "429", description = "Demasiados códigos MFA fallidos")
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
