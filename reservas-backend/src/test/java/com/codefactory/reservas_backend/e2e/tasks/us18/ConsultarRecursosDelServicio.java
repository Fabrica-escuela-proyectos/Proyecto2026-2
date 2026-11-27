package com.codefactory.reservas_backend.e2e.tasks.us18;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Get;

/** HU-18: consulta los recursos actualmente asignados a un servicio. */
public final class ConsultarRecursosDelServicio {

    private ConsultarRecursosDelServicio() {
    }

    public static Performable delServicio(String idDelServicio) {
        return Task.where("{0} consulta los recursos asignados al servicio", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Get.resource("/api/v1/services/" + idDelServicio + "/resources")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)));
        });
    }
}
