package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.BookingCancellationService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingItem;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.CancelBookingRequest;
import com.codefactory.reservas_backend.common.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = OpenApiTags.CLIENT_BOOKINGS)
@RequiredArgsConstructor
public class BookingCancellationController {

    private final BookingCancellationService cancellationService;
    private final IdentityService identityService;

    @Operation(summary = "Cancelar una reserva como cliente (HU-25)",
            description = "Solo el cliente dueño de la reserva. Debe estar CONFIRMADA y faltar al menos 1 hora para su inicio. Libera el horario. El cuerpo con `reason` (máx. 500) es opcional.")
    @ApiResponse(responseCode = "200", description = "Reserva cancelada (origen CLIENTE)")
    @ApiResponse(responseCode = "400", description = "Motivo demasiado largo")
    @ApiResponse(responseCode = "403", description = "La reserva es de otro cliente")
    @ApiResponse(responseCode = "404", description = "La reserva no existe")
    @ApiResponse(responseCode = "409", description = "Ya cancelada o completada, ya iniciada, o faltan menos de 1 hora para su inicio")
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
