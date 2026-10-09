package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.BookingService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingResponse;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.CreateBookingRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-22 - Crear reserva: POST /api/v1/bookings, solo para el rol CLIENTE
 * (un proveedor o administrador recibe 403; sin sesión, 401).
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final IdentityService identityService;

    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request,
                                                  HttpServletRequest httpRequest) {
        UserIdentity client = identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.create(request, client, httpRequest.getRemoteAddr()));
    }
}
