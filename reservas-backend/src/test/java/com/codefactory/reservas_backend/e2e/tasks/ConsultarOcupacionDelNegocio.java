package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConsultarReporte;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;

import java.util.Map;

public final class ConsultarOcupacionDelNegocio {

    private ConsultarOcupacionDelNegocio() {
    }

    public static Performable porDia(String idDelNegocio, String desde, String hasta) {
        return agrupada(idDelNegocio, desde, hasta, "DAY");
    }

    public static Performable porSemana(String idDelNegocio, String desde, String hasta) {
        return agrupada(idDelNegocio, desde, hasta, "WEEK");
    }

    private static Performable agrupada(String idDelNegocio, String desde, String hasta, String agrupacion) {
        Map<String, String> parametros = ConsultarReporte.periodo(desde, hasta);
        parametros.put("groupBy", agrupacion);
        return Task.where("{0} consulta la ocupacion del negocio del " + desde + " al " + hasta + " por " + agrupacion,
                ConsultarReporte.en("/api/v1/businesses/" + idDelNegocio + "/reports/occupancy", parametros));
    }
}
