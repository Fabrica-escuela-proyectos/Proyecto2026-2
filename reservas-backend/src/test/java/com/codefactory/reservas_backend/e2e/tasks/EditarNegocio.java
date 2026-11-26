package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.CuerpoJson;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.restassured.http.ContentType;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Patch;

import java.util.Map;

/** HU-07: el actor edita la información del negocio. Edición parcial: solo se envía lo que cambia. */
public final class EditarNegocio {

    private EditarNegocio() {
    }

    public static Performable conLosDatos(String idDelNegocio, Map<String, Object> cambios) {
        return Task.where("{0} edita el negocio con los datos " + cambios.keySet(), actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Patch.to("/api/v1/businesses/" + idDelNegocio)
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(CuerpoJson.de(cambios))));
        });
    }
}
