package com.codefactory.reservas_backend.report.controller;

import com.codefactory.reservas_backend.common.config.OpenApiTags;
import com.codefactory.reservas_backend.report.application.ReportPeriod;
import com.codefactory.reservas_backend.report.application.ReportService;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.PlatformSummaryReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

/**
 * HU-34 - Resumen de la plataforma: GET /api/v1/admin/reports/platform-summary. Solo ADMINISTRADOR; como todo
 * administrador, necesita el MFA activo (ADR-004, MfaEnrollmentFilter) y su sesión ya pasó el segundo factor.
 */
@RestController
@Tag(name = OpenApiTags.REPORTS)
@RequiredArgsConstructor
public class PlatformReportController {

    private final ReportService reportService;
    private final Clock clock;

    @Operation(summary = "Resumen de la plataforma (HU-34)",
            description = "Solo administrador (con MFA). Negocios activos (proveedor con cuenta habilitada), reservas por estado y ocupación general de los recursos activos. Periodo `from`/`to` (yyyy-MM-dd, inclusivos); por defecto los últimos 30 días, máximo 366. Es informativo: no hay cobros ni pagos.")
    @ApiResponse(responseCode = "200", description = "Resumen del periodo")
    @ApiResponse(responseCode = "400", description = "Fechas inválidas o inicio posterior al fin")
    @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR, o el administrador no ha activado su MFA")
    @GetMapping("/api/v1/admin/reports/platform-summary")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PlatformSummaryReport> platformSummary(@RequestParam(required = false) String from,
                                                                 @RequestParam(required = false) String to) {
        return ResponseEntity.ok(reportService.platformSummary(ReportPeriod.parse(from, to, LocalDate.now(clock))));
    }
}
