package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.CuerpoJson;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.restassured.http.ContentType;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Put;

import java.util.Map;

/**
 * HU-08: el actor define la antelación mínima (en horas) del negocio. Las horas son Object para poder enviar
 * también valores inválidos como "abc".
 */
public final class DefinirAntelacion {

    private DefinirAntelacion() {
    }

    public static Performable de(String idDelNegocio, Object horas) {
        return Task.where("{0} define una antelación mínima de " + horas + " horas", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Put.to("/api/v1/businesses/" + idDelNegocio + "/booking-lead-time")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(CuerpoJson.de(Map.of("hours", horas)))));
        });
    }
}
