package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConsultarReporte;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;

public final class ConsultarResumenDeLaPlataforma {

    private ConsultarResumenDeLaPlataforma() {
    }

    public static Performable delPeriodo(String desde, String hasta) {
        return Task.where("{0} consulta el resumen de la plataforma del " + desde + " al " + hasta,
                ConsultarReporte.en("/api/v1/admin/reports/platform-summary", ConsultarReporte.periodo(desde, hasta)));
    }
}
