package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

/** HU-04: el actor cierra su sesión. Si no tiene sesión recordada, hace la petición sin ella. */
public final class CerrarSesion {

    private CerrarSesion() {
    }

    public static Performable ahora() {
        return Task.where("{0} cierra su sesión", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            boolean sinSesion = token == null || token.isBlank();
            actor.attemptsTo(Post.to("/api/v1/auth/logout").with(request ->
                    sinSesion ? request : ConSesion.deAcceso(token).apply(request)));
        });
    }
}
