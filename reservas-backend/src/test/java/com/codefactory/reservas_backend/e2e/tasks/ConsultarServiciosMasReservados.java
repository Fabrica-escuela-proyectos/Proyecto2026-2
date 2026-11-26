package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConsultarReporte;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;

public final class ConsultarServiciosMasReservados {

    private ConsultarServiciosMasReservados() {
    }

    public static Performable delNegocio(String idDelNegocio, String desde, String hasta) {
        return Task.where("{0} consulta los servicios mas reservados del " + desde + " al " + hasta,
                ConsultarReporte.en("/api/v1/businesses/" + idDelNegocio + "/reports/top-services",
                        ConsultarReporte.periodo(desde, hasta)));
    }
}
