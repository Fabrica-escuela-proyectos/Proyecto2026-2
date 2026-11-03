package com.codefactory.reservas_backend.report.domain;

/** Reportes (HU-29..34): fechas con formato inválido, inicio posterior al fin, periodo mayor de 366 días o parámetro inválido → 400. */
public class InvalidReportPeriodException extends RuntimeException {
    public InvalidReportPeriodException(String message) {
        super(message);
    }
}
