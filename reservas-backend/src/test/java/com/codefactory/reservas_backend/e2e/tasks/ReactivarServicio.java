package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

/** HU-12: el actor reactiva un servicio desactivado (es el mismo servicio, no se crea otro). */
public final class ReactivarServicio {

    private ReactivarServicio() {
    }

    public static Performable conId(String idDelServicio) {
        return Task.where("{0} pide reactivar el servicio", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Post.to("/api/v1/services/" + idDelServicio + "/reactivation")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)));
        });
    }
}
