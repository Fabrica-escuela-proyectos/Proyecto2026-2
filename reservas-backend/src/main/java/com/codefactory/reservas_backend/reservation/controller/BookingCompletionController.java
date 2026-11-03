package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.common.config.OpenApiTags;
import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.BookingCompletionService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** HU-27 - Completar reserva: POST /api/v1/bookings/{bookingId}/completion (sin cuerpo). */
@RestController
@Tag(name = OpenApiTags.PROVIDER_BOOKINGS)
@RequiredArgsConstructor
public class BookingCompletionController {

    private final BookingCompletionService completionService;
    private final IdentityService identityService;

    @Operation(summary = "Marcar una reserva como completada (HU-27)",
            description = "Solo el proveedor dueño del negocio. La reserva debe estar CONFIRMADA y su hora de fin ya debe haber pasado. Queda en estado COMPLETADA (cuenta para los reportes del Sprint 3).")
    @ApiResponse(responseCode = "200", description = "Reserva completada")
    @ApiResponse(responseCode = "403", description = "La reserva es de un negocio de otro proveedor")
    @ApiResponse(responseCode = "404", description = "La reserva no existe")
    @ApiResponse(responseCode = "409", description = "Está cancelada, ya completada o todavía no termina")
    @PostMapping("/api/v1/bookings/{bookingId}/completion")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<BookingItem> complete(@PathVariable UUID bookingId, HttpServletRequest httpRequest) {
        UserIdentity provider = identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
        return ResponseEntity.ok(completionService.complete(bookingId, provider, httpRequest.getRemoteAddr()));
    }
}
