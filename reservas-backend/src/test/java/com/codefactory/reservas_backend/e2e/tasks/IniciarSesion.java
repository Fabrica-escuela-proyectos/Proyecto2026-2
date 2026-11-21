package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.models.Credenciales;
import com.codefactory.reservas_backend.e2e.models.Json;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

/**
 * HU-02: el actor inicia sesión. Si entra, recuerda su sesión (TOKEN) para que cualquier otra HU la reutilice;
 * si no entra, la sesión recordada queda vacía.
 */
public final class IniciarSesion {

    private IniciarSesion() {
    }

    public static Performable con(Credenciales credenciales) {
        return Task.where("{0} intenta iniciar sesión", actor -> {
            actor.remember(Memoria.CREDENCIALES, credenciales);
            actor.attemptsTo(Post.to("/api/v1/auth/login")
                    .with(request -> request.body(Json.de(credenciales.comoCuerpo()))));
            int estado = RespuestaDeLaApi.codigoDeEstado().answeredBy(actor);
            String token = estado == 200 ? RespuestaDeLaApi.token().answeredBy(actor) : "";
            actor.remember(Memoria.TOKEN, token);
        });
    }
}
