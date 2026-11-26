package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConsultarReporte;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;

public final class ConsultarReservasPorEstado {

    private ConsultarReservasPorEstado() {
    }

    public static Performable delNegocio(String idDelNegocio, String desde, String hasta) {
        return Task.where("{0} consulta las reservas por estado del " + desde + " al " + hasta,
                ConsultarReporte.en("/api/v1/businesses/" + idDelNegocio + "/reports/bookings-by-status",
                        ConsultarReporte.periodo(desde, hasta)));
    }
}
