package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.CuerpoJson;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.restassured.http.ContentType;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Patch;

import java.util.Map;

/** HU-10: el proveedor cambia el precio de un servicio (edición parcial). */
public final class CambiarPrecioDelServicio {

    private CambiarPrecioDelServicio() {
    }

    public static Performable a(String idDelServicio, long nuevoPrecio) {
        return Task.where("{0} cambia el precio del servicio a " + nuevoPrecio, actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Patch.to("/api/v1/services/" + idDelServicio)
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(CuerpoJson.de(Map.of("priceCop", nuevoPrecio)))));
        });
    }
}
