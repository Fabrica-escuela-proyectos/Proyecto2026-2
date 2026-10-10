package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.BookingQueryService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BusinessBookingPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-24 - Consultar las reservas del negocio. Solo el PROVEEDOR dueño del negocio
 * (otro proveedor, cliente o administrador: 403; sin sesión: 401; negocio inexistente: 404).
 */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/bookings")
@RequiredArgsConstructor
public class BusinessBookingsController {

    private final BookingQueryService bookingQueryService;
    private final IdentityService identityService;

    @GetMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<BusinessBookingPageResponse> list(
            @PathVariable UUID businessId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + BookingQueryService.DEFAULT_SIZE) int size) {
        UserIdentity provider = identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
        return ResponseEntity.ok(bookingQueryService.listForBusiness(businessId, provider, from, to, status, page, size));
    }
}
