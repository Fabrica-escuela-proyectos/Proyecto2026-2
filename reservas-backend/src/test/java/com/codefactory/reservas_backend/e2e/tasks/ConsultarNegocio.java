package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Get;

/**
 * HU-13 (catálogo): cualquier usuario con sesión consulta un negocio. La respuesta trae sus datos (HU-07) y solo
 * los servicios activos (HU-09, HU-11 y HU-12).
 */
public final class ConsultarNegocio {

    private ConsultarNegocio() {
    }

    public static Performable conId(String idDelNegocio) {
        return Task.where("{0} consulta el negocio en el catálogo", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Get.resource("/api/v1/businesses/" + idDelNegocio)
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)));
        });
    }
}
