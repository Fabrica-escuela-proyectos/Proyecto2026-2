package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Get;

/** HU-24 (reservas del negocio) y HU-23 (mis reservas, la usa HU-26 para ver el motivo). */
public final class ConsultarReservas {

    private ConsultarReservas() {
    }

    public static Performable delNegocio(String idDelNegocio) {
        return Task.where("{0} consulta las reservas de un negocio", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Get.resource("/api/v1/businesses/" + idDelNegocio + "/bookings")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)));
        });
    }

    public static Performable propias() {
        return Task.where("{0} consulta sus reservas", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Get.resource("/api/v1/bookings/me")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)));
        });
    }

    public static Performable delUsuario(String idDelUsuario) {
        return Task.where("{0} consulta las reservas del usuario " + idDelUsuario, actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Get.resource("/api/v1/users/" + idDelUsuario + "/bookings")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)));
        });
    }
}
