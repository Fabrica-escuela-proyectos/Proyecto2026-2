package com.codefactory.reservas_backend.resource.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.resource.application.ResourceAvailabilityService;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.AvailabilityResponse;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.DayRangesRequest;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.WeekScheduleRequest;
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
@RequestMapping("/api/v1/resources/{resourceId}/availability")
@RequiredArgsConstructor
public class ResourceAvailabilityController {

    private final ResourceAvailabilityService availabilityService;
    private final IdentityService identityService;

    @GetMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<AvailabilityResponse> get(@PathVariable UUID resourceId) {
        return ResponseEntity.ok(availabilityService.get(resourceId, currentUser()));
    }

    @PutMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<AvailabilityResponse> replaceWeek(
            @PathVariable UUID resourceId,
            @Valid @RequestBody WeekScheduleRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(availabilityService.replaceWeek(
                resourceId, request, currentUser(), httpRequest.getRemoteAddr()));
    }

    @PutMapping("/{dayOfWeek}")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<AvailabilityResponse> replaceDay(
            @PathVariable UUID resourceId,
            @PathVariable int dayOfWeek,
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
