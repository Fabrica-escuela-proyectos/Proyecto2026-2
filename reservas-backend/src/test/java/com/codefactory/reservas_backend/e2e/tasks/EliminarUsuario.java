package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Delete;

/** HU-05: el actor elimina una cuenta. El código de verificación adicional es opcional (puede ir null). */
public final class EliminarUsuario {

    private EliminarUsuario() {
    }

    public static Performable con(String idDelUsuario, String codigoMfa) {
        return Task.where("{0} elimina una cuenta", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Delete.from("/api/v1/users/" + idDelUsuario).with(request -> {
                var conSesion = ConSesion.deAccesoOpcional(token).apply(request);
                return codigoMfa == null ? conSesion : conSesion.header("X-MFA-Code", codigoMfa);
            }));
        });
    }
}
