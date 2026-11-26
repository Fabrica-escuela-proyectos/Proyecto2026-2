package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Get;

/** HU-13: el actor consulta el listado de negocios o el detalle de uno (con sus servicios activos). */
public final class ConsultarCatalogo {

    private ConsultarCatalogo() {
    }

    public static Performable listado() {
        return Task.where("{0} consulta el catálogo de negocios", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Get.resource("/api/v1/businesses")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)));
        });
    }

    public static Performable detalleDe(String idDelNegocio) {
        return Task.where("{0} consulta el detalle de un negocio", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Get.resource("/api/v1/businesses/" + idDelNegocio)
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)));
        });
    }
}
