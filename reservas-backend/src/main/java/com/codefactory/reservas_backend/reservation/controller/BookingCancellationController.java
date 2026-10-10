package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.BookingCancellationService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingItem;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.CancelBookingRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-25 - Cancelar reserva como cliente: POST /api/v1/bookings/{bookingId}/cancellation.
 * Solo el rol CLIENTE y solo sobre sus propias reservas (de otro cliente: 403). El cuerpo es opcional.
 */
@RestController
@RequiredArgsConstructor
public class BookingCancellationController {

    private final BookingCancellationService cancellationService;
    private final IdentityService identityService;

    @PostMapping("/api/v1/bookings/{bookingId}/cancellation")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<BookingItem> cancel(@PathVariable UUID bookingId,
                                              @Valid @RequestBody(required = false) CancelBookingRequest request,
                                              HttpServletRequest httpRequest) {
        UserIdentity client = identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(cancellationService.cancelAsClient(bookingId, reason, client, httpRequest.getRemoteAddr()));
    }
}
