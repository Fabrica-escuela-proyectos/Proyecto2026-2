package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.Json;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Patch;

import java.util.Map;

/** HU-05: el actor cambia el rol de un usuario. El código de verificación adicional es opcional (puede ir null). */
public final class CambiarRol {

    private CambiarRol() {
    }

    public static Performable de(String idDelUsuario, String rol, String codigoMfa) {
        return Task.where("{0} cambia el rol de un usuario", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Patch.to("/api/v1/users/" + idDelUsuario + "/role").with(request -> {
                var conSesion = ConSesion.deAccesoOpcional(token).apply(request);
                var conCodigo = codigoMfa == null ? conSesion : conSesion.header("X-MFA-Code", codigoMfa);
                return conCodigo.body(Json.de(Map.of("role", rol)));
            }));
        });
    }
}
