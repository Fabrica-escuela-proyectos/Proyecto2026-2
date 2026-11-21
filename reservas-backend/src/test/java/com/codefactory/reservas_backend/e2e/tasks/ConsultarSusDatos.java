package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Get;

/** Operación protegida de referencia: el actor consulta sus propios datos con la sesión que tiene recordada. */
public final class ConsultarSusDatos {

    private ConsultarSusDatos() {
    }

    public static Performable conSuSesion() {
        return Task.where("{0} consulta sus propios datos", actor -> {
            String id = actor.recall(Memoria.ID_DE_USUARIO);
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Get.resource("/api/v1/users/" + id)
                    .with(request -> ConSesion.deAcceso(token).apply(request)));
        });
    }
}
