package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

/** HU-17: el proveedor reactiva un recurso desactivado (el mismo recurso, sin registrarlo de nuevo). */
public final class ReactivarRecurso {

    private ReactivarRecurso() {
    }

    public static Performable conId(String idDelRecurso) {
        return Task.where("{0} solicita reactivar un recurso", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Post.to("/api/v1/resources/" + idDelRecurso + "/reactivation")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)));
        });
    }
}
