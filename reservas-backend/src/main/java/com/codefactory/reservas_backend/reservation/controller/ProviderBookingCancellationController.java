package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.BookingCancellationService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingItem;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.ProviderCancelRequest;
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
 * HU-26 - Cancelar reserva como proveedor: POST /api/v1/bookings/{bookingId}/provider-cancellation.
 * Solo el PROVEEDOR dueño del negocio de la reserva; el motivo es obligatorio.
 */
@RestController
@RequiredArgsConstructor
public class ProviderBookingCancellationController {

    private final BookingCancellationService cancellationService;
    private final IdentityService identityService;

    @PostMapping("/api/v1/bookings/{bookingId}/provider-cancellation")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<BookingItem> cancel(@PathVariable UUID bookingId,
                                              @Valid @RequestBody ProviderCancelRequest request,
                                              HttpServletRequest httpRequest) {
        UserIdentity provider = identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
        return ResponseEntity.ok(cancellationService.cancelAsProvider(
                bookingId, request.getReason(), provider, httpRequest.getRemoteAddr()));
    }
}
