package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.CuerpoJson;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.restassured.http.ContentType;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

import java.util.Map;

/**
 * HU-11: el actor desactiva un servicio. Si el servicio tiene reservas futuras, la API pide confirmación;
 * con "confirm": true se desactiva y se cancelan esas reservas.
 */
public final class DesactivarServicio {

    private DesactivarServicio() {
    }

    public static Performable sinConfirmar(String idDelServicio) {
        return Task.where("{0} pide desactivar el servicio", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Post.to("/api/v1/services/" + idDelServicio + "/deactivation")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)));
        });
    }

    public static Performable confirmando(String idDelServicio) {
        return Task.where("{0} pide desactivar el servicio y confirma la operación", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Post.to("/api/v1/services/" + idDelServicio + "/deactivation")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(CuerpoJson.de(Map.of("confirm", true)))));
        });
    }
}
