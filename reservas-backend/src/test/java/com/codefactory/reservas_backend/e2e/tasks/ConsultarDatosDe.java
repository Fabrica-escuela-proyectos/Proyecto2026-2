package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Get;

/** El actor consulta los datos de un usuario concreto con la sesión que tiene recordada. */
public final class ConsultarDatosDe {

    private ConsultarDatosDe() {
    }

    public static Performable elUsuario(String idDelUsuario) {
        return Task.where("{0} consulta los datos de un usuario", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Get.resource("/api/v1/users/" + idDelUsuario)
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)));
        });
    }
}
