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
 * HU-09: el actor envía un servicio con los datos que se le pasen, válidos o no (un campo vacío, un texto donde va
 * un número, un nombre muy largo...). CrearServicio, que hizo el equipo, solo acepta datos ya tipados.
 */
public final class RegistrarServicio {

    private RegistrarServicio() {
    }

    public static Performable conLosDatos(String idDelNegocio, Map<String, Object> datos) {
        return Task.where("{0} registra un servicio con los datos " + datos.keySet(), actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Post.to("/api/v1/businesses/" + idDelNegocio + "/services")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(CuerpoJson.de(datos))));
        });
    }
}
