package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Get;

/** HU-14: el proveedor consulta los recursos de su negocio (también ven los cambios de HU-15, HU-16 y HU-17). */
public final class ConsultarRecursos {

    private ConsultarRecursos() {
    }

    public static Performable delNegocio(String idDelNegocio) {
        return Task.where("{0} consulta los recursos de su negocio", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Get.resource("/api/v1/businesses/" + idDelNegocio + "/resources")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)));
        });
    }
}
