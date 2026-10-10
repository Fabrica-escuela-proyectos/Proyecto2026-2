package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.BookingQueryService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingPageResponse;
import com.codefactory.reservas_backend.common.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = OpenApiTags.CLIENT_BOOKINGS)
@RequiredArgsConstructor
public class MyBookingsController {

    private final BookingQueryService bookingQueryService;
    private final IdentityService identityService;

    @Operation(summary = "Mis reservas (HU-23)",
            description = "Solo CLIENTE y solo las suyas. Orden: fecha de inicio descendente. Incluye las canceladas con su origen y motivo.")
    @ApiResponse(responseCode = "200", description = "Página de reservas del cliente")
    @ApiResponse(responseCode = "400", description = "Estado o paginación inválidos")
    @GetMapping("/api/v1/bookings/me")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<BookingPageResponse> mine(
            @Parameter(description = "Filtrar por estado: CONFIRMADA, CANCELADA o COMPLETADA (sin distinguir mayúsculas)", example = "CONFIRMADA") @RequestParam(required = false) String status,
            @Parameter(description = "Página, desde 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página: por defecto 20, máximo 50 (si se pide más, se recorta)") @RequestParam(defaultValue = "" + BookingQueryService.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(bookingQueryService.listOwn(currentUser(), status, page, size));
    }

    @Operation(summary = "Reservas de un usuario (HU-23)",
            description = "Devuelve lo mismo que /bookings/me si `userId` es el del cliente autenticado; si es de otro usuario responde 403, exista o no ese usuario.")
    @ApiResponse(responseCode = "200", description = "Página de reservas del cliente")
    @ApiResponse(responseCode = "400", description = "Estado o paginación inválidos")
    @ApiResponse(responseCode = "403", description = "El userId es de otro usuario")
    @GetMapping("/api/v1/users/{userId}/bookings")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<BookingPageResponse> ofUser(
            @PathVariable UUID userId,
            @Parameter(description = "Filtrar por estado: CONFIRMADA, CANCELADA o COMPLETADA (sin distinguir mayúsculas)", example = "CONFIRMADA") @RequestParam(required = false) String status,
            @Parameter(description = "Página, desde 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página: por defecto 20, máximo 50 (si se pide más, se recorta)") @RequestParam(defaultValue = "" + BookingQueryService.DEFAULT_SIZE) int size) {
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
