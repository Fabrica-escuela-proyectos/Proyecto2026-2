package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.BookingCancellationService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingItem;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.ProviderCancelRequest;
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
 * HU-26 - Cancelar reserva como proveedor: POST /api/v1/bookings/{bookingId}/provider-cancellation.
 * Solo el PROVEEDOR dueño del negocio de la reserva; el motivo es obligatorio.
 */
@RestController
@Tag(name = OpenApiTags.PROVIDER_BOOKINGS)
@RequiredArgsConstructor
public class ProviderBookingCancellationController {

    private final BookingCancellationService cancellationService;
    private final IdentityService identityService;

    @Operation(summary = "Cancelar una reserva como proveedor (HU-26)",
            description = "Solo el proveedor dueño del negocio de la reserva. El motivo es obligatorio (máx. 500). No aplica la regla de 1 hora, pero no se puede cancelar una reserva que ya inició.")
    @ApiResponse(responseCode = "200", description = "Reserva cancelada (origen PROVEEDOR)")
    @ApiResponse(responseCode = "400", description = "Falta el motivo o es demasiado largo")
    @ApiResponse(responseCode = "403", description = "La reserva es de un negocio de otro proveedor")
    @ApiResponse(responseCode = "404", description = "La reserva no existe")
    @ApiResponse(responseCode = "409", description = "Ya cancelada o completada, o la reserva ya inició")
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
