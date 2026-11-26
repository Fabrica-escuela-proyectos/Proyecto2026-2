package com.codefactory.reservas_backend.e2e.tasks;

import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Get;

/** HU-20: cualquier persona (es pública) consulta los horarios libres de un servicio en una fecha. */
public final class ConsultarDisponibilidad {

    private ConsultarDisponibilidad() {
    }

    public static Performable delServicio(String idDelServicio, String fecha) {
        return Task.where("{0} consulta la disponibilidad del servicio el " + fecha, actor ->
                actor.attemptsTo(Get.resource("/api/v1/services/" + idDelServicio + "/availability")
                        .with(request -> request.queryParam("date", fecha))));
    }
}
