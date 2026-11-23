package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

/** HU-27: el proveedor marca una reserva de su negocio como completada. */
public final class CompletarReserva {

    private CompletarReserva() {
    }

    public static Performable conId(String idDeLaReserva) {
        return Task.where("{0} marca la reserva como completada", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Post.to("/api/v1/bookings/" + idDeLaReserva + "/completion")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)));
        });
    }
}
