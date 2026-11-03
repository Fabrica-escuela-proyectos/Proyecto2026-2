package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.common.config.OpenApiTags;
import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.ResourceClosureBookingService;
import com.codefactory.reservas_backend.reservation.application.ResourceClosureBookingService.ClosureResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-21 - Bloquear un horario puntual: POST /api/v1/resources/{resourceId}/closures. La consulta y la
 * eliminación de cierres están en el módulo de recursos (ResourceClosureQueryController).
 */
@RestController
@Tag(name = OpenApiTags.RESOURCES)
@RequiredArgsConstructor
public class ResourceClosureController {

    private final ResourceClosureBookingService closureBookingService;
    private final IdentityService identityService;

    @Getter
    @Setter
    public static class CloseResourceRequest {

        @Schema(description = "Día completo a cerrar, yyyy-MM-dd (no se combina con start/end)", example = "2026-12-08")
        private String date;

        @Schema(description = "Inicio de un cierre parcial, yyyy-MM-dd HH:mm (hora de Bogotá)", example = "2026-11-20 14:00")
        private String start;

        @Schema(description = "Fin de un cierre parcial, yyyy-MM-dd HH:mm; posterior al inicio", example = "2026-11-20 18:00")
        private String end;

        @Schema(description = "Motivo (opcional, máx. 300)", example = "Mantenimiento del equipo")
        @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
        private String reason;

        @Schema(description = "true para aceptar que se cancelen las reservas que caen en el cierre")
        private boolean confirm;
    }

    @Operation(summary = "Cerrar un recurso en una fecha u horario puntual (HU-21)",
            description = "Solo el proveedor dueño. Se envía `date` (día completo) **o** `start` y `end` (rango con fecha y hora). El horario semanal no cambia: el cierre solo se descuenta de la disponibilidad (HU-20) y de las reservas nuevas (HU-22). Si hay reservas confirmadas en ese periodo y no se envía `confirm: true`, responde 409 CONFIRMATION_REQUIRED con la cantidad y no cambia nada; con confirmación las cancela (origen RECURSO_NO_DISPONIBLE). No se permiten cierres en el pasado ni traslapados con otro cierre.")
    @ApiResponse(responseCode = "201", description = "Cierre registrado (con las reservas canceladas, si hubo)")
    @ApiResponse(responseCode = "400", description = "Periodo con formato u orden inválido, en el pasado o mayor de 366 días")
    @ApiResponse(responseCode = "403", description = "El recurso es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El recurso no existe")
    @ApiResponse(responseCode = "409", description = "CONFIRMATION_REQUIRED (hay reservas afectadas) o se cruza con otro cierre")
    @PostMapping("/api/v1/resources/{resourceId}/closures")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<ClosureResult> close(@PathVariable UUID resourceId,
                                               @Valid @RequestBody CloseResourceRequest request,
                                               HttpServletRequest httpRequest) {
        UserIdentity requester = identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
        ClosureResult result = closureBookingService.close(resourceId, request.getDate(), request.getStart(),
                request.getEnd(), request.getReason(), request.isConfirm(), requester, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
