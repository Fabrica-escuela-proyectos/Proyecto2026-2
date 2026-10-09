package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.BookingQueryService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-23 - Consultar mis reservas. Solo el rol CLIENTE y solo las suyas.
 * {@code GET /api/v1/bookings/me} es la ruta normal; {@code GET /api/v1/users/{userId}/bookings}
 * devuelve lo mismo si {@code userId} es el del propio cliente y 403 si es de otro usuario (es la
 * ruta que ejercita el escenario "intenta ver las reservas de otro cliente").
 */
@RestController
@RequiredArgsConstructor
public class MyBookingsController {

    private final BookingQueryService bookingQueryService;
    private final IdentityService identityService;

    @GetMapping("/api/v1/bookings/me")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<BookingPageResponse> mine(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + BookingQueryService.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(bookingQueryService.listOwn(currentUser(), status, page, size));
    }

    @GetMapping("/api/v1/users/{userId}/bookings")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<BookingPageResponse> ofUser(
            @PathVariable UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + BookingQueryService.DEFAULT_SIZE) int size) {
        UserIdentity me = currentUser();
        if (!me.id().equals(userId)) {
            // Mismo 403 exista o no el otro usuario: no se revela nada sobre él.
            throw new AccessDeniedException("Un cliente solo puede ver sus propias reservas");
        }
        return ResponseEntity.ok(bookingQueryService.listOwn(me, status, page, size));
    }

    private UserIdentity currentUser() {
        return identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
    }
}
