package com.codefactory.reservas_backend.report.controller.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Respuestas de los reportes del Sprint 3 (HU-29..34). Las fechas son días de Bogotá; los minutos son de atención
 * efectiva (horario semanal menos cierres) y de reservas confirmadas o completadas. {@code occupancyPercent} es
 * nulo cuando no hay minutos disponibles. {@code message} solo viene cuando el reporte está vacío.
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** HU-29: una fila por recurso activo del negocio. */
    public record ResourceOccupancy(UUID resourceId, String name, String type, long availableMinutes, long bookedMinutes,
                                    Double occupancyPercent) {
    }

    public record ResourceOccupancyReport(UUID businessId, LocalDate from, LocalDate to, List<ResourceOccupancy> resources,
                                          long availableMinutes, long bookedMinutes, Double occupancyPercent, String message) {
    }

    /** HU-30: un día o una semana (lunes a domingo, recortada al periodo). */
    public record PeriodOccupancy(LocalDate start, LocalDate end, long availableMinutes, long bookedMinutes,
                                  Double occupancyPercent) {
    }

    public record BusinessOccupancyReport(UUID businessId, LocalDate from, LocalDate to, String groupBy,
                                          List<PeriodOccupancy> periods, PeriodOccupancy highestDemand,
                                          PeriodOccupancy lowestDemand, Double occupancyPercent, String message) {
    }

    /** HU-31 (y parte de HU-34). */
    public record BookingsByStatusReport(UUID businessId, LocalDate from, LocalDate to, long confirmed, long cancelled,
                                         long completed, long total, Map<String, Long> cancelledByOrigin, String message) {
    }

    /** HU-32: servicios ordenados de más a menos reservados (confirmadas + completadas). */
    public record ServiceRanking(int rank, UUID serviceId, String serviceName, long bookings, long cancelled) {
    }

    public record TopServicesReport(UUID businessId, LocalDate from, LocalDate to, List<ServiceRanking> services, String message) {
    }

    /** HU-33: valores en COP según el precio guardado en cada reserva; es una referencia, no un registro de pagos. */
    public record BookingValueReport(UUID businessId, LocalDate from, LocalDate to, String currency, long completedValue,
                                     long confirmedValue, long totalValue, long cancelledValue, String note, String message) {
    }

    /** HU-34: resumen de toda la plataforma para el administrador. */
    public record PlatformSummaryReport(LocalDate from, LocalDate to, long activeBusinesses, long confirmed, long cancelled,
                                        long completed, long totalBookings, long availableMinutes, long bookedMinutes,
                                        Double occupancyPercent, String message) {
    }
}
