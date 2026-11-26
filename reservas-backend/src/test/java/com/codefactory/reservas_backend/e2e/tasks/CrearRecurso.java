package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.Json;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

import java.util.Map;
import java.util.UUID;

/** HU-06: el actor crea un recurso en un negocio. Sirve como "gestionar recursos" para probar la pertenencia. */
public final class CrearRecurso {

    private CrearRecurso() {
    }

    public static Performable enElNegocio(String idDelNegocio) {
        return Task.where("{0} crea un recurso en un negocio", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            String cuerpo = Json.de(Map.of("name", "Sala " + UUID.randomUUID().toString().substring(0, 6), "type", "SALA"));
            actor.attemptsTo(Post.to("/api/v1/businesses/" + idDelNegocio + "/resources")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request).body(cuerpo)));
        });
    }
}
