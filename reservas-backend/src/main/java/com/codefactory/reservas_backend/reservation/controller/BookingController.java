package com.codefactory.reservas_backend.reservation.controller;

import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.application.BookingService;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingResponse;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.CreateBookingRequest;
import com.codefactory.reservas_backend.common.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = OpenApiTags.CLIENT_BOOKINGS)
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final IdentityService identityService;

    @Operation(summary = "Crear una reserva (HU-22)",
            description = "Solo CLIENTE; el cliente sale de la sesión. Se envían `date`, `startTime` y `endTime` (hora de Bogotá) y la duración debe ser la del servicio. Con `resourceId` se reserva ese recurso; sin él se asigna el primer recurso libre por nombre. Anti-overbooking en la base de datos: un recurso no puede tener dos reservas confirmadas que se traslapen.")
    @ApiResponse(responseCode = "201", description = "Reserva CONFIRMADA")
    @ApiResponse(responseCode = "400", description = "Fecha, horas, duración, antelación mínima o recurso no asignado al servicio")
    @ApiResponse(responseCode = "404", description = "El servicio no está disponible")
    @ApiResponse(responseCode = "409", description = "Horario ocupado o fuera del horario de atención del recurso")
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
