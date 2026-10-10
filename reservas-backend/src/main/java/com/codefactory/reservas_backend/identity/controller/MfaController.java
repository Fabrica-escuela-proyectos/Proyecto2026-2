package com.codefactory.reservas_backend.identity.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.MfaService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.identity.controller.dto.MfaActivateRequest;
import com.codefactory.reservas_backend.identity.controller.dto.MfaSetupResponse;
import com.codefactory.reservas_backend.identity.domain.InvalidCredentialsException;
import com.codefactory.reservas_backend.common.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Enrolamiento de MFA (TOTP) para el usuario autenticado. No documentado
 * como endpoint propio en endpoints-sprint-1.md (ese documento da por hecho
 * la existencia de un mecanismo de MFA sin definir su contrato), pero es
 * necesario para que HU-02 ("verificación adicional para cuentas
 * administrativas") y HU-05 ("evento obligatorio para la creación de MFA")
 * sean alcanzables en la práctica y no solo un flag sin forma de activarse
 * — ver docs/matriz-actualizaciones.md.
 *
 * Requiere autenticación (no está en la lista permitAll de SecurityConfig);
 * disponible para cualquier rol autenticado, no solo ADMINISTRADOR, ya que
 * nada impide que un Cliente/Proveedor active MFA voluntariamente.
 */
@RestController
@Tag(name = OpenApiTags.AUTH)
@RequestMapping("/api/v1/auth/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;
    private final IdentityService identityService;

    @Operation(summary = "Iniciar el enrolamiento de MFA (ADR-004)",
            description = "Genera un secreto TOTP nuevo y la URI otpauth:// para la app autenticadora. La MFA no queda activa hasta confirmarla con /auth/mfa/activate. Un administrador sin MFA activa solo puede usar este endpoint, /activate y el cierre de sesión.")
    @ApiResponse(responseCode = "200", description = "Secreto y URI para el código QR")
    @PostMapping("/setup")
    public ResponseEntity<MfaSetupResponse> setup() {
        return ResponseEntity.ok(mfaService.setup(currentUserId()));
    }

    @Operation(summary = "Activar la MFA con un primer código (ADR-004)",
            description = "Confirma que la app autenticadora quedó bien configurada enviando un código vigente.")
    @ApiResponse(responseCode = "204", description = "MFA activada")
    @ApiResponse(responseCode = "400", description = "Código inválido, o no se inició el enrolamiento con /setup")
    @PostMapping("/activate")
    public ResponseEntity<Void> activate(@Valid @RequestBody MfaActivateRequest request) {
        mfaService.activate(currentUserId(), request.getCode());
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId() {
        UserIdentity current = identityService.getCurrentUser()
                .orElseThrow(() -> new InvalidCredentialsException("No hay una sesión activa"));
        return current.id();
    }
}
