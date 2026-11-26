package com.codefactory.reservas_backend.e2e.models;

import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;

/**
 * Los escenarios están en inglés ("Cancelled", "Client"...); la API usa los nombres en español
 * (CANCELADA, CLIENTE...). Aquí se traducen. También convierte valores en pesos como "$30.000".
 */
public final class EstadosDeReserva {

    private EstadosDeReserva() {
    }

    public static BookingStatus estado(String etiqueta) {
        return switch (etiqueta) {
            case "Confirmed" -> BookingStatus.CONFIRMADA;
            case "Cancelled" -> BookingStatus.CANCELADA;
            case "Completed" -> BookingStatus.COMPLETADA;
            default -> throw new IllegalArgumentException("Estado desconocido: " + etiqueta);
        };
    }

    public static CancelOrigin origen(String etiqueta) {
        return switch (etiqueta) {
            case "Client" -> CancelOrigin.CLIENTE;
            case "Provider" -> CancelOrigin.PROVEEDOR;
            case "Account deletion" -> CancelOrigin.ELIMINACION_CUENTA;
            default -> throw new IllegalArgumentException("Origen desconocido: " + etiqueta);
        };
    }

    /** "$30.000" -> 30000 (pesos colombianos, sin decimales). */
    public static long pesos(String valor) {
        String digitos = valor.replaceAll("\\D", "");
        return digitos.isEmpty() ? 0 : Long.parseLong(digitos);
    }
}
