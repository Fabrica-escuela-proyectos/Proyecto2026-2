package com.codefactory.reservas_backend.report.application;

import com.codefactory.reservas_backend.common.config.TimeConfig;
import com.codefactory.reservas_backend.report.domain.InvalidReportPeriodException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Periodo de un reporte: días de Bogotá [from, to], ambos inclusive (decisión D3 del plan del Sprint 3). Sin
 * fechas son los últimos 30 días hasta hoy; con una sola, la otra se completa a 30 días.
 */
public record ReportPeriod(LocalDate from, LocalDate to) {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 366;

    static final String FORMAT_MESSAGE = "Las fechas del periodo no son válidas: use el formato yyyy-MM-dd";
    static final String ORDER_MESSAGE = "El periodo no es válido: la fecha de inicio no puede ser posterior a la de fin";
    static final String TOO_LONG_MESSAGE = "El periodo no puede superar " + MAX_DAYS + " días";

    public static ReportPeriod parse(String fromText, String toText, LocalDate today) {
        LocalDate from = parseDate(fromText);
        LocalDate to = parseDate(toText);
        if (from == null && to == null) {
            to = today;
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_DAYS - 1L);
        }
        if (to == null) {
            to = from.plusDays(DEFAULT_DAYS - 1L);
        }
        if (from.isAfter(to)) {
            throw new InvalidReportPeriodException(ORDER_MESSAGE);
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new InvalidReportPeriodException(TOO_LONG_MESSAGE);
        }
        return new ReportPeriod(from, to);
    }

    /** Primer instante del periodo (00:00 de {@code from} en Bogotá). */
    public Instant start() {
        return from.atStartOfDay(TimeConfig.BUSINESS_ZONE).toInstant();
    }

    /** Instante en que termina el periodo (00:00 del día siguiente a {@code to}), excluido. */
    public Instant end() {
        return to.plusDays(1).atStartOfDay(TimeConfig.BUSINESS_ZONE).toInstant();
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidReportPeriodException(FORMAT_MESSAGE);
        }
    }
}
