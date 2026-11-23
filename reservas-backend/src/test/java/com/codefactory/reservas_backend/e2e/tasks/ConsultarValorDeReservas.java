package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Get;

/** HU-33: el proveedor consulta el valor de las reservas de su negocio en un periodo (fechas tal cual se escriben). */
public final class ConsultarValorDeReservas {

    private ConsultarValorDeReservas() {
    }

    public static Performable delNegocio(String idDelNegocio, String desde, String hasta) {
        return Task.where("{0} consulta el valor de las reservas del " + desde + " al " + hasta, actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Get.resource("/api/v1/businesses/" + idDelNegocio + "/reports/booking-value")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .queryParam("from", desde).queryParam("to", hasta)));
        });
    }
}
