package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.CuerpoJson;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.restassured.http.ContentType;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Patch;

import java.util.Map;

/** HU-10: el actor edita un servicio. Es una edición parcial: solo se envían los datos que cambian. */
public final class EditarServicio {

    private EditarServicio() {
    }

    public static Performable conLosDatos(String idDelServicio, Map<String, Object> cambios) {
        return Task.where("{0} edita el servicio con los datos " + cambios.keySet(), actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Patch.to("/api/v1/services/" + idDelServicio)
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(CuerpoJson.de(cambios))));
        });
    }
}
