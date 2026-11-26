package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConsultarReporte;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;

public final class ConsultarOcupacionDeRecursos {

    private ConsultarOcupacionDeRecursos() {
    }

    public static Performable delNegocio(String idDelNegocio, String desde, String hasta) {
        return Task.where("{0} consulta la ocupacion de los recursos del " + desde + " al " + hasta,
                ConsultarReporte.en("/api/v1/businesses/" + idDelNegocio + "/reports/resource-occupancy",
                        ConsultarReporte.periodo(desde, hasta)));
    }
}
