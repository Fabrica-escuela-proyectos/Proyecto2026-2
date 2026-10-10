package com.codefactory.reservas_backend.service.controller;

import com.codefactory.reservas_backend.service.application.CatalogService;
import com.codefactory.reservas_backend.service.controller.dto.CatalogDtos.BusinessDetailResponse;
import com.codefactory.reservas_backend.service.controller.dto.CatalogDtos.BusinessPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-13 - Consultar negocios y servicios. Solo lectura, para cualquier usuario
 * autenticado (sin sesión: 401; la regla general de SecurityConfig). A diferencia
 * de este catálogo, HU-20 (disponibilidad) es pública: ver ServiceAvailabilityController.
 */
@RestController
@RequestMapping("/api/v1/businesses")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping
    public ResponseEntity<BusinessPageResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + CatalogService.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(catalogService.listBusinesses(page, size));
    }

    @GetMapping("/{businessId}")
    public ResponseEntity<BusinessDetailResponse> detail(@PathVariable UUID businessId) {
        return ResponseEntity.ok(catalogService.getBusiness(businessId));
    }
}
