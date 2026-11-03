package com.codefactory.reservas_backend.report.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.BookingValueReport;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.BookingsByStatusReport;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.BusinessOccupancyReport;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.PlatformSummaryReport;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.ResourceOccupancyReport;
import com.codefactory.reservas_backend.report.controller.dto.ReportDtos.TopServicesReport;

import java.util.UUID;

/**
 * Reportes del Sprint 3 (HU-29..34). Módulo de solo lectura, sin tablas propias (ADR-008): compone lo que exponen
 * reservas (agregados), recursos (horario efectivo), servicios y proveedores. Los de negocio exigen al PROVEEDOR
 * dueño; el de plataforma, al ADMINISTRADOR (el control de rol está en el controlador).
 */
public interface ReportService {

    int DEFAULT_TOP = 10;
    int MAX_TOP = 50;

    ResourceOccupancyReport resourceOccupancy(UUID businessId, ReportPeriod period, UserIdentity requester);

    /** @param groupBy {@code DAY} o {@code WEEK} (sin distinguir mayúsculas; por defecto DAY) */
    BusinessOccupancyReport businessOccupancy(UUID businessId, ReportPeriod period, String groupBy, UserIdentity requester);

    BookingsByStatusReport bookingsByStatus(UUID businessId, ReportPeriod period, UserIdentity requester);

    TopServicesReport topServices(UUID businessId, ReportPeriod period, Integer limit, UserIdentity requester);

    BookingValueReport bookingValue(UUID businessId, ReportPeriod period, UserIdentity requester);

    PlatformSummaryReport platformSummary(ReportPeriod period);
}
