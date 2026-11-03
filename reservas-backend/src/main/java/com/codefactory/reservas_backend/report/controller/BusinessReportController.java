package com.codefactory.reservas_backend.report.controller;

import com.codefactory.reservas_backend.common.config.OpenApiTags;
import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.report.application.ReportPeriod;
import com.codefactory.reservas_backend.report.application.ReportService;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.BookingValueReport;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.BookingsByStatusReport;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.BusinessOccupancyReport;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.ResourceOccupancyReport;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.TopServicesReport;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Reportes del negocio (HU-29..33): GET /api/v1/businesses/{businessId}/reports/*. Solo el PROVEEDOR dueño; el
 * cliente recibe 403. Periodo {@code from}/{@code to} (yyyy-MM-dd, días de Bogotá, inclusivos; por defecto los
 * últimos 30 días; máximo 366).
 */
@RestController
@Tag(name = OpenApiTags.REPORTS)
@RequestMapping("/api/v1/businesses/{businessId}/reports")
@RequiredArgsConstructor
public class BusinessReportController {

    private static final String PERIOD = " Periodo: `from` y `to` (yyyy-MM-dd, inclusivos, hora de Bogotá); por defecto los últimos 30 días, máximo 366. Solo el proveedor dueño del negocio.";

    private final ReportService reportService;
    private final IdentityService identityService;
    private final Clock clock;

    @Operation(summary = "Ocupación por recurso (HU-29)",
            description = "Por cada recurso activo: minutos disponibles (horario semanal menos cierres), minutos reservados (reservas confirmadas y completadas) y porcentaje de ocupación, de más a menos usado." + PERIOD)
    @ApiResponse(responseCode = "200", description = "Ocupación por recurso (vacía con `message` si no hay datos)")
    @ApiResponse(responseCode = "400", description = "Fechas inválidas o inicio posterior al fin")
    @ApiResponse(responseCode = "403", description = "El negocio es de otro proveedor o el rol no es PROVEEDOR")
    @ApiResponse(responseCode = "404", description = "El negocio no existe")
    @GetMapping("/resource-occupancy")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<ResourceOccupancyReport> resourceOccupancy(@PathVariable UUID businessId,
                                                                     @RequestParam(required = false) String from,
                                                                     @RequestParam(required = false) String to) {
        return ResponseEntity.ok(reportService.resourceOccupancy(businessId, period(from, to), currentUser()));
    }

    @Operation(summary = "Ocupación del negocio por día o semana (HU-30)",
            description = "Ocupación de todos los recursos activos agrupada por día (`groupBy=DAY`, por defecto) o por semana de lunes a domingo (`WEEK`), con el periodo de mayor y de menor demanda." + PERIOD)
    @ApiResponse(responseCode = "200", description = "Ocupación por periodo")
    @ApiResponse(responseCode = "400", description = "Fechas o `groupBy` inválidos")
    @ApiResponse(responseCode = "403", description = "El negocio es de otro proveedor o el rol no es PROVEEDOR")
    @ApiResponse(responseCode = "404", description = "El negocio no existe")
    @GetMapping("/occupancy")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<BusinessOccupancyReport> businessOccupancy(@PathVariable UUID businessId,
                                                                     @RequestParam(required = false) String from,
                                                                     @RequestParam(required = false) String to,
                                                                     @Parameter(description = "DAY o WEEK") @RequestParam(required = false) String groupBy) {
        return ResponseEntity.ok(reportService.businessOccupancy(businessId, period(from, to), groupBy, currentUser()));
    }

    @Operation(summary = "Reservas por estado (HU-31)",
            description = "Cantidad de reservas confirmadas, canceladas (con su origen) y completadas cuyo inicio cae en el periodo." + PERIOD)
    @ApiResponse(responseCode = "200", description = "Conteo por estado")
    @ApiResponse(responseCode = "400", description = "Fechas inválidas o inicio posterior al fin")
    @ApiResponse(responseCode = "403", description = "El negocio es de otro proveedor o el rol no es PROVEEDOR")
    @ApiResponse(responseCode = "404", description = "El negocio no existe")
    @GetMapping("/bookings-by-status")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<BookingsByStatusReport> bookingsByStatus(@PathVariable UUID businessId,
                                                                   @RequestParam(required = false) String from,
                                                                   @RequestParam(required = false) String to) {
        return ResponseEntity.ok(reportService.bookingsByStatus(businessId, period(from, to), currentUser()));
    }

    @Operation(summary = "Servicios más reservados (HU-32)",
            description = "Servicios ordenados por reservas confirmadas + completadas (las canceladas se muestran aparte). `limit` de 1 a 50, por defecto 10." + PERIOD)
    @ApiResponse(responseCode = "200", description = "Ranking de servicios")
    @ApiResponse(responseCode = "400", description = "Fechas o `limit` inválidos")
    @ApiResponse(responseCode = "403", description = "El negocio es de otro proveedor o el rol no es PROVEEDOR")
    @ApiResponse(responseCode = "404", description = "El negocio no existe")
    @GetMapping("/top-services")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<TopServicesReport> topServices(@PathVariable UUID businessId,
                                                         @RequestParam(required = false) String from,
                                                         @RequestParam(required = false) String to,
                                                         @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(reportService.topServices(businessId, period(from, to), limit, currentUser()));
    }

    @Operation(summary = "Valor de las reservas (HU-33)",
            description = "Suma en COP del precio guardado en cada reserva: completadas (realizado), confirmadas (comprometido), total de ambas y, aparte, canceladas. Es una referencia: la plataforma no maneja pagos." + PERIOD)
    @ApiResponse(responseCode = "200", description = "Valores del periodo")
    @ApiResponse(responseCode = "400", description = "Fechas inválidas o inicio posterior al fin")
    @ApiResponse(responseCode = "403", description = "El negocio es de otro proveedor o el rol no es PROVEEDOR")
    @ApiResponse(responseCode = "404", description = "El negocio no existe")
    @GetMapping("/booking-value")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<BookingValueReport> bookingValue(@PathVariable UUID businessId,
                                                           @RequestParam(required = false) String from,
                                                           @RequestParam(required = false) String to) {
        return ResponseEntity.ok(reportService.bookingValue(businessId, period(from, to), currentUser()));
    }

    private ReportPeriod period(String from, String to) {
        return ReportPeriod.parse(from, to, LocalDate.now(clock));
    }

    private UserIdentity currentUser() {
        return identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
    }
}
