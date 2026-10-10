package com.codefactory.reservas_backend.resource.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.resource.application.ResourceAvailabilityService;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.AvailabilityResponse;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.DayRangesRequest;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.WeekScheduleRequest;
import com.codefactory.reservas_backend.common.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-19 - Definir horarios de atención de un recurso. Solo el PROVEEDOR dueño
 * del negocio del recurso (docs/api/endpoints-sprint-2.md).
 */
@RestController
@Tag(name = OpenApiTags.RESOURCES)
@RequestMapping("/api/v1/resources/{resourceId}/availability")
@RequiredArgsConstructor
public class ResourceAvailabilityController {

    private final ResourceAvailabilityService availabilityService;
    private final IdentityService identityService;

    @Operation(summary = "Consultar el horario semanal de un recurso (HU-19)",
            description = "Siempre devuelve los 7 días (1 = lunes … 7 = domingo) en hora de Bogotá; los días sin rangos están no disponibles.")
    @ApiResponse(responseCode = "200", description = "Horario semanal")
    @ApiResponse(responseCode = "403", description = "El recurso es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El recurso no existe")
    @GetMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<AvailabilityResponse> get(@PathVariable UUID resourceId) {
        return ResponseEntity.ok(availabilityService.get(resourceId, currentUser()));
    }

    @Operation(summary = "Reemplazar el horario semanal (HU-19)",
            description = "Los días omitidos quedan sin disponibilidad. Rangos HH:mm [inicio, fin), sin superposición y con un máximo de 10 por día. Atómico: si algo es inválido se conserva el horario anterior.")
    @ApiResponse(responseCode = "200", description = "Horario semanal actualizado")
    @ApiResponse(responseCode = "400", description = "Formato de hora inválido, inicio no anterior al fin, rangos superpuestos o día repetido")
    @ApiResponse(responseCode = "403", description = "El recurso es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El recurso no existe")
    @PutMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<AvailabilityResponse> replaceWeek(
            @PathVariable UUID resourceId,
            @Valid @RequestBody WeekScheduleRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(availabilityService.replaceWeek(
                resourceId, request, currentUser(), httpRequest.getRemoteAddr()));
    }

    @Operation(summary = "Reemplazar el horario de un día (HU-19)",
            description = "`ranges` vacío deja el día no disponible. Los demás días no cambian.")
    @ApiResponse(responseCode = "200", description = "Horario semanal tras el cambio")
    @ApiResponse(responseCode = "400", description = "Día fuera de 1–7, formato de hora inválido o rangos superpuestos")
    @ApiResponse(responseCode = "403", description = "El recurso es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El recurso no existe")
    @PutMapping("/{dayOfWeek}")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<AvailabilityResponse> replaceDay(
            @PathVariable UUID resourceId,
            @Parameter(description = "Día ISO: 1 = lunes … 7 = domingo", example = "1") @PathVariable int dayOfWeek,
            @Valid @RequestBody DayRangesRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(availabilityService.replaceDay(
                resourceId, dayOfWeek, request.ranges(), currentUser(), httpRequest.getRemoteAddr()));
    }

    private UserIdentity currentUser() {
        return identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
    }
}
