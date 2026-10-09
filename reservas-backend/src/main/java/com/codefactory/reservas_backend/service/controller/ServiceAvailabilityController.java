package com.codefactory.reservas_backend.service.controller;

import com.codefactory.reservas_backend.service.application.ServiceAvailabilityService;
import com.codefactory.reservas_backend.service.controller.dto.ServiceAvailabilityDtos.ServiceAvailabilityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-20 - Consultar disponibilidad de un servicio. Solo lectura y PÚBLICA (sin
 * sesión), como pide el escenario "usuario sin sesión iniciada" de la HU: la
 * excepción está en SecurityConfig y solo cubre este GET.
 */
@RestController
@RequestMapping("/api/v1/services/{serviceId}/availability")
@RequiredArgsConstructor
public class ServiceAvailabilityController {

    private final ServiceAvailabilityService availabilityService;

    @GetMapping
    public ResponseEntity<ServiceAvailabilityResponse> get(
            @PathVariable UUID serviceId,
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(availabilityService.getAvailability(serviceId, date));
    }
}
